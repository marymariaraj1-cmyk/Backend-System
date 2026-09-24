package com.billing.dto;

import java.math.BigDecimal;

public class DailyCashBookSaveRequest {

    private String bookDate;
    private BigDecimal rentAmt;
    private BigDecimal expenseAmt;
    private BigDecimal chitAmt;
    private BigDecimal financeAmt;
    private BigDecimal noteAmt;
    private BigDecimal salaryAmt;
    private BigDecimal coinAmt;
    private BigDecimal cashInHandAmt;
    private BigDecimal openingBalance;
    private String remarks;

    public DailyCashBookSaveRequest() {
    }

    public String getBookDate() {
        return bookDate;
    }

    public void setBookDate(String bookDate) {
        this.bookDate = bookDate;
    }

    public BigDecimal getRentAmt() {
        return rentAmt;
    }

    public void setRentAmt(BigDecimal rentAmt) {
        this.rentAmt = rentAmt;
    }

    public BigDecimal getExpenseAmt() {
        return expenseAmt;
    }

    public void setExpenseAmt(BigDecimal expenseAmt) {
        this.expenseAmt = expenseAmt;
    }

    public BigDecimal getChitAmt() {
        return chitAmt;
    }

    public void setChitAmt(BigDecimal chitAmt) {
        this.chitAmt = chitAmt;
    }

    public BigDecimal getFinanceAmt() {
        return financeAmt;
    }

    public void setFinanceAmt(BigDecimal financeAmt) {
        this.financeAmt = financeAmt;
    }

    public BigDecimal getNoteAmt() {
        return noteAmt;
    }

    public void setNoteAmt(BigDecimal noteAmt) {
        this.noteAmt = noteAmt;
    }

    public BigDecimal getSalaryAmt() {
        return salaryAmt;
    }

    public void setSalaryAmt(BigDecimal salaryAmt) {
        this.salaryAmt = salaryAmt;
    }

    public BigDecimal getCoinAmt() {
        return coinAmt;
    }

    public void setCoinAmt(BigDecimal coinAmt) {
        this.coinAmt = coinAmt;
    }

    public BigDecimal getCashInHandAmt() {
        return cashInHandAmt;
    }

    public void setCashInHandAmt(BigDecimal cashInHandAmt) {
        this.cashInHandAmt = cashInHandAmt;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
