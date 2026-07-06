package com.geihou.module.supplychain.stock.controller.admin.vo;

import com.geihou.module.supplychain.api.stock.dto.ReconcileReportRespDTO;

/**
 * Admin response VO for the reconciliation report (G2-02J).
 *
 * <p>Currently wraps the {@link ReconcileReportRespDTO} directly. If additional
 * metadata (pagination, truncation flag) is needed in the future, extend this VO.
 *
 * <p>Review note: the DTO already carries {@code inferredAdjustmentSign} on each
 * item, so the sign inference is transparent in the API output.
 */
public class ReconcileReportRespVO {

    private ReconcileReportRespDTO report;

    public ReconcileReportRespDTO getReport() { return report; }
    public void setReport(ReconcileReportRespDTO report) { this.report = report; }
}
