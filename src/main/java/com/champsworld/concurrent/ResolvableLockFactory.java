package com.champsworld.concurrent;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author agrsachin81
 */
public class ResolvableLockFactory {

    //if thread dies then it will be removed from this map if he is still owns some lock
    //if lock is not used by any thread and ready to be garbage collected then the value will be null inside list item
    private final static WeakHashMap<Thread, List<WeakReference<ResolvedLockImpl>>> threadLockMap = new WeakHashMap<>();

    //if Lock is not used by any thread and ready to be gc, then the  key will not be present inside map
    // while the weakReference make sure that Thread will always be garbage collected
    private final static WeakHashMap<ResolvedLockImpl, WeakReference<Thread>> lockOwners = new WeakHashMap<>();

    //if Lock is not used by any thread and ready to be gc, then the  key will not be present inside map
    private final static WeakHashMap<Thread, WeakReference<Thread>> threadWeakReferences = new WeakHashMap<>();

    private final static WeakHashMap<Thread, WeakReference<ResolvedLockImpl>> blockedQueue = new WeakHashMap<>();

    /**
     * Return existing WeakReference if any or creates new
     * @param thread
     * @return
     */
    private static WeakReference<Thread> getWeakReference(final Thread thread){
        // no need to thread safety as Thread itself is the key
        return threadWeakReferences.computeIfAbsent( thread, k -> new WeakReference<>(thread));
    }

    private static Thread getLockOwner(ResolvedLockImpl lock) {
        synchronized (lockOwners) {
            WeakReference<Thread> threadWeakReference = lockOwners.get(lock);
            if (threadWeakReference == null) return null;
            return threadWeakReference.get();
        }
    }

    private static void removeFromBlockedQ(ResolvedLockImpl lock) {
        blockedQueue.remove(Thread.currentThread(), lock.weakSelf);
    }

    /**
     *
     * @param lock whose ownership needs to be changed
     * @param addToBlockedQ, CurrentThread added to blocked queue when the method returns false
     * @return true when lock ownership set, or false if already owned by someone else
     */
    private static boolean setLockOwner(final ResolvedLockImpl lock, boolean addToBlockedQ) {
       final WeakReference<Thread> weakReference = getWeakReference(Thread.currentThread());
        synchronized (lockOwners) {
            if (lockOwners.containsKey(lock)) {
                final WeakReference<Thread> currentOwner = lockOwners.get(lock);
                //case when thread is gc but lock is not
                if (currentOwner.get() == null) {
                    lockOwners.remove(lock);
                    // return true after adding for current Thread; the mapping inside threadLockMap will already be removed
                } else {
                    if (currentOwner == weakReference) {
                        //add again to make sure reentrancy works
                        List<WeakReference<ResolvedLockImpl>> lockList = threadLockMap.computeIfAbsent(Thread.currentThread(), thr -> new ArrayList<>());
                        lockList.add(lock.weakSelf);
                        return true;
                    } else {
                        //someone else owns the lock
                        if(addToBlockedQ) {
                            blockedQueue.put(Thread.currentThread(), lock.weakSelf);
                        }
                        return false;
                    }
                }
            }
            List<WeakReference<ResolvedLockImpl>> lockList = threadLockMap.computeIfAbsent(Thread.currentThread(), thr -> new ArrayList<>());
            lockList.add(lock.weakSelf);
            lockOwners.put(lock, weakReference);
            return true;
        }
    }

    /**
     *
     * @param lock the lock whose ownership is terminated
     * @param force removes owner even if someone other than current thread owns the lock
     * @return true if successfully removed the owner or not present, false if someone else owns the lock
     */
    private static boolean removeOwner(ResolvedLockImpl lock, boolean force) {
        WeakReference<Thread> weakReference = getWeakReference(Thread.currentThread());
        synchronized (lockOwners) {
            if (lockOwners.containsKey(lock)) {
                final WeakReference<Thread> currentOwner = lockOwners.get(lock);
                //case when thread is gc but lock is not
                if (currentOwner.get() == null) {
                    lockOwners.remove(lock);
                    return true;
                    // the mapping inside threadLockMap will already be removed because of weakHash, the current owner doesn't own the lock
                } else {
                    if (currentOwner == weakReference || force) {
                        //add again to make sure reentrancy works
                        List<WeakReference<ResolvedLockImpl>> lockList = threadLockMap.computeIfAbsent(currentOwner.get(), thr -> Collections.emptyList());
                        boolean isEmptyList = Collections.EMPTY_LIST == lockList;
                        boolean result = isEmptyList || lockList.remove(lock.weakSelf);
                        if(force) {
                            //remove all reentrancy
                            while(result && !isEmptyList){
                                result = lockList.remove(lock.weakSelf);
                                isEmptyList = lockList.isEmpty();
                            }
                            lockOwners.remove(lock);
                            result = true;
                        } else if(result) {
                            if (!lockList.isEmpty() && lockList.contains(lock.weakSelf)) {
                                //for re-entrant functionality to work we can not remove other instances
                                return true;
                            } else {
                                lockOwners.remove(lock);
                            }
                        } else {
                            // INTERNAL ERROR it must have the reference there
                            // TODO: ERROR LOG
                        }
                        return result;
                    } else {
                        //someone else owns the lock
                        return false;
                    }
                }
            } else {
                //nobody owns the lock
                return true;
            }
        }
    }

    /**
     * This function must not throw
     * @param lock, the lock on which the specified thread is blocked on
     * @param blockedThread that is blocked on the specific lock
     * @return the lock instance owned by the specified blocked thread that is part of deadlock if exists, null if no deadlock found
     */
    private static WeakReference<ResolvedLockImpl> isDeadLocked(final ResolvedLockImpl lock,final Thread blockedThread) {
        //checking if still blocked or not anymore
        if(!blockedQueue.containsKey(blockedThread)) return null;
        //check how many locks owned by blockedThread, if no locks owned by the blocked thread it can not solve the deadlock
        final List<WeakReference<ResolvedLockImpl>> locksOwnedByBlockedThread = threadLockMap.get(blockedThread);
        if(locksOwnedByBlockedThread.isEmpty()) return null;
        // check which thread owns the lock for this
        ResolvedLockImpl toSearch = lock;
        while(toSearch!=null){
            final Thread ownerThread = getLockOwner(toSearch);
            if(ownerThread== null || ownerThread == blockedThread) return null;
            WeakReference<ResolvedLockImpl> reference = blockedQueue.get(ownerThread);
            if(reference ==null) return null;
            if(locksOwnedByBlockedThread.contains(reference)){
                // check if already released
                if(reference.get() ==null) return null;
                return reference;
            }
            toSearch = reference.get();
        }
        return null;
    }

    private static final Lock reentrantLock = new ReentrantLock();

    public static ResolvedLock createLock() {
        return new ResolvedLockImpl();
    }

    public interface ResolvedLock extends Closeable {
        void lock() throws InterruptedException;
        boolean tryLock(int timeout, TimeUnit unit) throws InterruptedException;

        boolean tryLock() throws InterruptedException;

        boolean unLock();

        default void close(){
            unLock();
        }
    }

    /**
     * This object must be immutable
     */
    private static class ResolvedLockImpl implements ResolvedLock{
        private final Semaphore binaryMutex  = new Semaphore(1);
        private final WeakReference<ResolvedLockImpl> weakSelf ;

        private ResolvedLockImpl() {
            this.weakSelf = new WeakReference<>(this);
        }

        @Override
        public void lock() throws InterruptedException{
            boolean result = tryLock(true);
            // already added the current thread to waiting queue
            if(result) {
                return;
            }
            //wait for lock or try to see if there is deadlock or not
            // and schedule a lock resolver task
            final Thread thread = Thread.currentThread();
            final AtomicReference<ResolvedLockImpl> toResolve = new AtomicReference<>(null);

            CompletableFuture<WeakReference<ResolvedLockImpl>> future = getWeakReferenceCompletableFuture(thread, this, toResolve, this);
            while(true){
                if(future.isDone() || future.isCancelled()) {
                    removeFromBlockedQ(this);
                    if(toResolve.get() ==null){
                        result = tryLock(true);
                        if(result) return;
                        toResolve.set(null);
                        future = getWeakReferenceCompletableFuture(thread, this, toResolve, this);
                    } else {
                        ResolvedLockImpl lockReference = toResolve.get();
                        lockReference.forceRelease(); // this will immediately unblock other thread
                        thread.interrupt();
                        throw new DeadLockedException("Interrupted after forcibly releasing lock", lockReference);
                    }
                } else {
                    synchronized (this) {
                        try {
                            this.wait(15, 1200);
                        } catch (Throwable ignored){}
                    }
                }
            }
            // after lock is granted remove from itself from the waiting queue
        }

        private static CompletableFuture<WeakReference<ResolvedLockImpl>> getWeakReferenceCompletableFuture(Thread thread, final ResolvedLockImpl lock,  final AtomicReference<ResolvedLockImpl> toResolve, final Object toNotify) {
            CompletableFuture<WeakReference<ResolvedLockImpl>> future = CompletableFuture.supplyAsync(() -> isDeadLocked(lock, thread));
            future.whenComplete( (toForceRelease, t)-> {
                //it is executed inside WorkStealing pool, which is essential to ensure this code runs after isDeadlocked method
                synchronized (toNotify) {
                    if (lock != null) {
                        // the blocked thread has to release the specified lock to resolve deadlock and interrupt itself
                        toResolve.set(toForceRelease.get());
                    }
                    toNotify.notifyAll();
                }
            });
            return future;
        }

        @Override
        public boolean tryLock(int timeout, TimeUnit unit) throws InterruptedException {
            boolean res= tryLock(true);
            if(res) return true;
            //TODO: else wait for the specified time period and return true or false
            return false;
        }

        @Override
        public boolean tryLock() throws InterruptedException {
            return tryLock(false);
        }

        public boolean tryLock(boolean addToBlockQ) throws InterruptedException {
            boolean result =  ResolvableLockFactory.setLockOwner(this, addToBlockQ);
            if(result)  binaryMutex.acquire();
            return result;
        }

        @Override
        public boolean unLock() {
            boolean result =  removeOwner(this, false);
            if(result) binaryMutex.release();
            return result;
        }

        private void forceRelease(){
            //ideally current thread is also the owner as it is called from
            boolean result =  removeOwner(this, true);
            if(result) binaryMutex.release();
        }
    }

    public static class DeadLockedException extends InterruptedException {
        private final ResolvedLock forcedReleaseLock;
        private DeadLockedException(ResolvedLock lock){
            forcedReleaseLock = lock;
        }

        private DeadLockedException(String s, ResolvedLock lock){
            super(s);
            this.forcedReleaseLock = lock;
        }

        public ResolvedLock getForcedReleaseLock() {
            return forcedReleaseLock;
        }
    }
}
