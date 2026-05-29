package io.github.emiliasamaemt.bluemaprailway.platform;

public interface PlatformScheduler {

    Cancellable runSync(Runnable task);

    Cancellable runSyncLater(Runnable task, long delayTicks);

    Cancellable runSyncRepeating(Runnable task, long delayTicks, long periodTicks);

    Cancellable runAsync(Runnable task);

    interface Cancellable {
        void cancel();
    }
}
