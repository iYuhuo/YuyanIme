package com.yuyan.imemodule.utils.thread

import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ThreadPoolUtils private constructor() {

    companion object {
        
        private var DEFAULT_EXECUTOR: ThreadPoolExecutor? = null

        
        private var SINGLETON_EXECUTOR: ThreadPoolExecutor? = null

        
        private var SCHEDULED_EXECUTOR: ScheduledThreadPoolExecutor? = null

        
        private var SCHEDULED_DAEMON_EXECUTOR: ScheduledThreadPoolExecutor? = null

        init {
            val cpuCount = Runtime.getRuntime().availableProcessors()
            val corePoolSize = max((cpuCount * 2).toDouble(), 4.0).toInt()
            DEFAULT_EXECUTOR = ThreadPoolExecutor(
                corePoolSize, corePoolSize * 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue(),
                NamingThreadFactory("ThreadPoolUtils")
            ) { r, executor ->
                try {
                    executor.queue.put(r)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            SCHEDULED_EXECUTOR = ScheduledThreadPoolExecutor(
                corePoolSize,
                NamingThreadFactory("ThreadPoolUtils-scheduled", false)
            )
            SCHEDULED_DAEMON_EXECUTOR = ScheduledThreadPoolExecutor(
                corePoolSize,
                NamingThreadFactory("ThreadPoolUtils-scheduled-daemon", true)
            )
            SINGLETON_EXECUTOR = newSingletonExecutor("ThreadPoolUtils-singleton")
        }

        fun execute(runnable: Runnable?) {
            DEFAULT_EXECUTOR!!.execute(runnable)
        }

        fun executeSingleton(runnable: Runnable?) {
            SINGLETON_EXECUTOR!!.execute(runnable)
        }

        
        fun schedule(runnable: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            return SCHEDULED_EXECUTOR!!.schedule(runnable, delay, unit)
        }

        
        fun scheduleAtFixedRate(
            runnable: Runnable?,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*> {
            return SCHEDULED_EXECUTOR!!.scheduleAtFixedRate(runnable, initialDelay, period, unit)
        }

        
        fun scheduleInDaemon(
            runnable: Runnable?,
            delay: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*> {
            return SCHEDULED_DAEMON_EXECUTOR!!.schedule(runnable, delay, unit)
        }

        
        fun scheduleAtFixedRateInDaemon(
            runnable: Runnable?,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*> {
            return SCHEDULED_DAEMON_EXECUTOR!!.scheduleAtFixedRate(
                runnable,
                initialDelay,
                period,
                unit
            )
        }

        
        fun newSingletonExecutor(threadPoolName: String?): ThreadPoolExecutor {
            return ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
                NamingThreadFactory(threadPoolName!!)
            )
        }

        
        fun newScheduledExecutor(
            threadPoolName: String?,
            daemon: Boolean
        ): ScheduledThreadPoolExecutor {
            return ScheduledThreadPoolExecutor(
                1, NamingThreadFactory(
                    threadPoolName!!, daemon
                )
            )
        }

        
        fun cancel(future: Future<*>) {
            future.cancel(true)
        }
    }
}
