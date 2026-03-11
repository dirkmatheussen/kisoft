package com.knapp.kisoft.mock.service;

import com.knapp.kisoft.mock.api.dto.StorageOrder;
import com.knapp.kisoft.mock.api.dto.StorageOrderReply;
import com.knapp.kisoft.mock.service.MockDataStore.StorageOrderWithStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Advances storage order processing status (NEW → STARTED → FINISHED) after random delays
 * between 10 and 60 seconds, and triggers StorageOrderReply webhooks on each change.
 */
@Service
public class StorageOrderStatusAdvancer {

    private static final Logger log = LoggerFactory.getLogger(StorageOrderStatusAdvancer.class);
    private static final int MIN_DELAY_SECONDS = 10;
    private static final int MAX_DELAY_SECONDS = 60;

    private final MockDataStore dataStore;
    private final ReplyCallbackService replyCallbackService;
    private final ScheduledExecutorService scheduler;

    public StorageOrderStatusAdvancer(MockDataStore dataStore,
                                      ReplyCallbackService replyCallbackService,
                                      ScheduledExecutorService storageOrderScheduler) {
        this.dataStore = dataStore;
        this.replyCallbackService = replyCallbackService;
        this.scheduler = storageOrderScheduler;
    }

    /**
     * Schedule status transitions for a newly created storage order.
     * NEW → STARTED after 10–60s, then STARTED → FINISHED after 10–60s; webhook sent on each change.
     */
    public void scheduleTransitions(String clientNumber, String orderNumber) {
        int delaySeconds = randomDelaySeconds();
        log.debug("Scheduling NEW→STARTED for {} in {}s", orderNumber, delaySeconds);
        scheduler.schedule(() -> advanceToStarted(clientNumber, orderNumber), delaySeconds, TimeUnit.SECONDS);
    }

    private void advanceToStarted(String clientNumber, String orderNumber) {
        StorageOrderWithStatus withStatus = dataStore.getStorageOrder(clientNumber, orderNumber);
        if (withStatus == null || !"NEW".equals(withStatus.getProcessingStatus())) {
            log.debug("Order {} no longer NEW, skipping STARTED transition", orderNumber);
            return;
        }
        withStatus.setProcessingStatus("STARTED");
        StorageOrder order = withStatus.getOrder();
        sendReply(order, "STARTED");
        int delaySeconds = randomDelaySeconds();
        log.debug("Scheduling STARTED→FINISHED for {} in {}s", orderNumber, delaySeconds);
        scheduler.schedule(() -> advanceToFinished(clientNumber, orderNumber), delaySeconds, TimeUnit.SECONDS);
    }

    private void advanceToFinished(String clientNumber, String orderNumber) {
        StorageOrderWithStatus withStatus = dataStore.getStorageOrder(clientNumber, orderNumber);
        if (withStatus == null || !"STARTED".equals(withStatus.getProcessingStatus())) {
            log.debug("Order {} no longer STARTED, skipping FINISHED transition", orderNumber);
            return;
        }
        withStatus.setProcessingStatus("FINISHED");
        StorageOrder order = withStatus.getOrder();
        sendReply(order, "FINISHED");
    }

    private void sendReply(StorageOrder order, String processingStatus) {
        StorageOrderReply reply = new StorageOrderReply(
                order.clientNumber(),
                order.orderNumber(),
                order.businessCase() != null ? order.businessCase() : "GOODS_IN",
                "SYSTEM",
                processingStatus,
                Instant.now().toString(),
                order.loadUnitCode(),
                order.loadCarrier(),
                order.contentType()
        );
        replyCallbackService.sendStorageOrderReply(reply);
    }

    private static int randomDelaySeconds() {
        return MIN_DELAY_SECONDS + ThreadLocalRandom.current().nextInt(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS + 1);
    }
}
