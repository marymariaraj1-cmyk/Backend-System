package com.billing.service.impl;

import com.billing.dao.CashBookFieldConfigDao;
import com.billing.dto.CashBookFieldConfigRequest;
import com.billing.dto.ManualAmountEntry;
import com.billing.entity.CashBookFieldConfig;
import com.billing.exception.FieldInUseException;
import com.billing.service.CashBookFieldConfigService;
import com.billing.util.ManualValuesUtil;
import com.billing.util.SalesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
public class CashBookFieldConfigServiceImpl implements CashBookFieldConfigService {

    private static final Logger logger = LoggerFactory.getLogger(CashBookFieldConfigServiceImpl.class);

    private static final int MAX_FIELDS_PER_TYPE = 25;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final DataSource dataSource;
    private final CashBookFieldConfigDao configDao;

    public CashBookFieldConfigServiceImpl(DataSource dataSource, CashBookFieldConfigDao configDao) {
        this.dataSource = dataSource;
        this.configDao = configDao;
    }

    @Override
    public List<Map<String, Object>> listFields(Long clientId) {
        List<CashBookFieldConfig> configs = configDao.findByClientId(clientId);
        Map<String, List<LocalDate>> usedDatesByKey = scanUsedDates(clientId, null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CashBookFieldConfig config : configs) {
            List<LocalDate> dates = usedDatesByKey.getOrDefault(
                    usageKey(config.getFieldType(), config.getFieldLabel()), List.of());
            result.add(toMap(config, !dates.isEmpty(), dates));
        }
        return result;
    }

    @Override
    public Map<String, Object> create(Long clientId, String clientUsername, CashBookFieldConfigRequest request) {
        String type = normalizeType(request != null ? request.getFieldType() : null);
        String label = ManualValuesUtil.normalizeLabel(request.getLabel());
        String labelTa = normalizeTa(request.getLabelTa());

        if (configDao.countByClientIdAndTypeAndLabel(clientId, type, label) > 0) {
            throw new IllegalArgumentException("This label already exists on this side");
        }
        int existing = configDao.countByClientIdAndType(clientId, type);
        if (existing >= MAX_FIELDS_PER_TYPE) {
            throw new IllegalArgumentException("Maximum " + MAX_FIELDS_PER_TYPE + " fields per type");
        }
        Integer max = configDao.maxDisplayOrder(clientId, type);

        return withTransaction(conn -> {
            CashBookFieldConfig config = new CashBookFieldConfig();
            config.setClientId(clientId);
            config.setClientUsername(clientUsername);
            config.setFieldLabel(label);
            config.setFieldLabelTa(labelTa);
            config.setFieldType(type);
            config.setDisplayOrder((max != null ? max : 0) + 1);
            config.setCreatedBy(clientUsername);
            config.setCreatedDate(SalesUtil.now());
            config.setUpdatedBy(clientUsername);
            config.setUpdatedDate(SalesUtil.now());
            Long id = configDao.insert(config, conn);
            config.setFieldConfigId(id);
            return toMap(config, false, List.of());
        });
    }

    @Override
    public Map<String, Object> update(Long clientId, String clientUsername, CashBookFieldConfigRequest request) {
        if (request == null || request.getFieldConfigId() == null) {
            throw new IllegalArgumentException("Field id is required");
        }
        String label = ManualValuesUtil.normalizeLabel(request.getLabel());
        String labelTa = normalizeTa(request.getLabelTa());
        Long fieldConfigId = request.getFieldConfigId();

        return withTransaction(conn -> {
            CashBookFieldConfig existing = configDao.findByIdAndClientId(fieldConfigId, clientId, conn);
            if (existing == null) {
                throw new IllegalArgumentException("Field not found for this client");
            }
            boolean duplicateLabel = configDao.findByClientIdAndType(clientId, existing.getFieldType())
                    .stream()
                    .anyMatch(f -> !f.getFieldConfigId().equals(fieldConfigId)
                            && f.getFieldLabel().trim().equalsIgnoreCase(label));
            if (duplicateLabel) {
                throw new IllegalArgumentException("This label already exists on this side");
            }
            existing.setFieldLabel(label);
            existing.setFieldLabelTa(labelTa);
            existing.setUpdatedBy(clientUsername);
            existing.setUpdatedDate(SalesUtil.now());
            int updated = configDao.update(existing, conn);
            if (updated == 0) {
                throw new IllegalArgumentException("Field not found for this client");
            }
            return toMap(existing, false, List.of());
        });
    }

    @Override
    public void delete(Long clientId, Long fieldConfigId) {
        if (fieldConfigId == null) {
            throw new IllegalArgumentException("Field id is required");
        }
        withTransaction(conn -> {
            CashBookFieldConfig existing = configDao.findByIdAndClientId(fieldConfigId, clientId, conn);
            if (existing == null) {
                throw new IllegalArgumentException("Field not found for this client");
            }
            List<LocalDate> usedDates = collectUsedDates(clientId, conn, existing.getFieldType(),
                    existing.getFieldLabel());
            if (!usedDates.isEmpty()) {
                throw new FieldInUseException(buildDeleteGuardMessage(existing.getFieldLabel(), usedDates));
            }
            int deleted = configDao.deleteByIdAndClientId(fieldConfigId, clientId, conn);
            if (deleted == 0) {
                throw new IllegalArgumentException("Field not found for this client");
            }
            return null;
        });
    }

    @Override
    public void reorder(Long clientId, String fieldType, List<Long> orderedIds) {
        String type = normalizeType(fieldType);
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new IllegalArgumentException("Ordered field list is required");
        }
        Set<Long> ownedIds = new HashSet<>(configDao.findIdsByClientIdAndType(clientId, type));
        if (orderedIds.size() != ownedIds.size() || !ownedIds.containsAll(orderedIds)) {
            throw new IllegalArgumentException("One or more fields were not found for this client");
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : orderedIds) {
            if (id == null || !seen.add(id)) {
                throw new IllegalArgumentException("Ordered field list contains a duplicate or invalid id");
            }
        }
        withTransaction(conn -> {
            int order = 1;
            for (Long id : orderedIds) {
                configDao.updateDisplayOrder(id, clientId, order++, conn);
            }
            return null;
        });
    }

    /** Scans the client's saved manual-value rows once and maps usage key -> dates. */
    private Map<String, List<LocalDate>> scanUsedDates(Long clientId, Connection conn) {
        List<Map<String, Object>> rows = conn != null
                ? configDao.findManualValueRowsByClient(clientId, conn)
                : configDao.findManualValueRowsByClient(clientId);
        Map<String, List<LocalDate>> used = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate bookDate = (LocalDate) row.get("bookDate");
            collectSide(used, bookDate, "DEBIT", (String) row.get("manualDebitValues"));
            collectSide(used, bookDate, "CREDIT", (String) row.get("manualCreditValues"));
        }
        return used;
    }

    private void collectSide(Map<String, List<LocalDate>> used, LocalDate bookDate, String type, String values) {
        if (values == null) {
            return;
        }
        for (ManualAmountEntry entry : ManualValuesUtil.parse(values)) {
            if (entry.getAmount() != null && entry.getAmount().signum() > 0) {
                used.computeIfAbsent(usageKey(type, entry.getLabel()), k -> new ArrayList<>()).add(bookDate);
            }
        }
    }

    private List<LocalDate> collectUsedDates(Long clientId, Connection conn, String type, String label) {
        List<LocalDate> dates = new ArrayList<>();
        List<Map<String, Object>> rows = configDao.findManualValueRowsByClient(clientId, conn);
        String targetColumn = "DEBIT".equals(type) ? "manualDebitValues" : "manualCreditValues";
        for (Map<String, Object> row : rows) {
            String values = (String) row.get(targetColumn);
            if (values != null && ManualValuesUtil.isUsed(values, label)) {
                dates.add((LocalDate) row.get("bookDate"));
            }
        }
        dates.sort(Comparator.reverseOrder());
        return dates;
    }

    private Map<String, Object> toMap(CashBookFieldConfig config, boolean inUse, List<LocalDate> dates) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldConfigId", config.getFieldConfigId());
        result.put("label", config.getFieldLabel());
        result.put("labelTa", config.getFieldLabelTa());
        result.put("fieldType", config.getFieldType());
        result.put("displayOrder", config.getDisplayOrder());
        result.put("inUse", inUse);
        List<String> formatted = new ArrayList<>();
        for (int i = 0; i < dates.size() && i < 5; i++) {
            formatted.add(DATE_FORMAT.format(dates.get(i)));
        }
        result.put("recentDates", formatted);
        return result;
    }

    private String usageKey(String type, String label) {
        return type + "|" + label.trim().toLowerCase();
    }

    private String normalizeType(String fieldType) {
        if (fieldType == null) {
            throw new IllegalArgumentException("Field type is required");
        }
        String type = fieldType.trim().toUpperCase();
        if (!"DEBIT".equals(type) && !"CREDIT".equals(type)) {
            throw new IllegalArgumentException("Field type must be DEBIT or CREDIT");
        }
        return type;
    }

    private String normalizeTa(String labelTa) {
        if (labelTa == null) {
            return null;
        }
        String trimmed = labelTa.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Tamil label must be at most 100 characters");
        }
        return trimmed;
    }

    private String buildDeleteGuardMessage(String label, List<LocalDate> dates) {
        StringBuilder message = new StringBuilder("Cannot delete '").append(label).append("'.");
        if (!dates.isEmpty()) {
            StringBuilder listed = new StringBuilder();
            int shown = Math.min(dates.size(), 5);
            for (int i = 0; i < shown; i++) {
                if (listed.length() > 0) {
                    listed.append(", ");
                }
                listed.append(DATE_FORMAT.format(dates.get(i)));
            }
            message.append(" It has saved amounts on ").append(listed);
            if (dates.size() > shown) {
                message.append(" (and ").append(dates.size() - shown).append(" more)");
            }
            message.append(".");
        } else {
            message.append(" It has saved amounts on past entries.");
        }
        message.append(" Set the amount to 0 or blank and save on the Daily Cash Book first.");
        message.append(" Past dates cannot be edited, so a field with saved amounts on a past date cannot be deleted.");
        return message.toString();
    }

    private <T> T withTransaction(Function<Connection, T> action) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            T result = action.apply(conn);
            conn.commit();
            return result;
        } catch (RuntimeException e) {
            logger.error("CashBookFieldConfig transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("CashBookFieldConfig rollback failed", rollbackEx);
                }
            }
            throw e;
        } catch (Exception e) {
            logger.error("CashBookFieldConfig transaction failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("CashBookFieldConfig rollback failed", rollbackEx);
                }
            }
            throw new RuntimeException("Failed to save field configuration", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeEx) {
                    logger.error("CashBookFieldConfig close failed", closeEx);
                }
            }
        }
    }
}