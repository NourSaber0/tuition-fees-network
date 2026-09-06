package com.tuitionnetwork.billing.service;

import com.tuitionnetwork.billing.dto.CreateFeeLineCommand;

import java.util.List;
import java.util.UUID;

public interface BillingFeeCommandService {

    List<UUID> createFeeLines(UUID institutionId, List<CreateFeeLineCommand> commands);

    void cancelFeeLine(UUID institutionId, UUID feeLineId);
}
