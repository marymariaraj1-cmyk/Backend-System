package com.billing.dto;

import java.util.List;

public class DailyCashBookSaveRequest {

    private String bookDate;
    private List<ManualAmountEntry> manualDebitEntries;
    private List<ManualAmountEntry> manualCreditEntries;
    private String remarks;

    public DailyCashBookSaveRequest() {
    }

    public String getBookDate() {
        return bookDate;
    }

    public void setBookDate(String bookDate) {
        this.bookDate = bookDate;
    }

    public List<ManualAmountEntry> getManualDebitEntries() {
        return manualDebitEntries;
    }

    public void setManualDebitEntries(List<ManualAmountEntry> manualDebitEntries) {
        this.manualDebitEntries = manualDebitEntries;
    }

    public List<ManualAmountEntry> getManualCreditEntries() {
        return manualCreditEntries;
    }

    public void setManualCreditEntries(List<ManualAmountEntry> manualCreditEntries) {
        this.manualCreditEntries = manualCreditEntries;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}