package com.threeline.merchantwallet.merchant.repository;

import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.domain.MerchantStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static com.threeline.merchantwallet.common.db.JdbcColumns.enumValue;
import static com.threeline.merchantwallet.common.db.JdbcColumns.instant;

public final class MerchantRowMapper implements RowMapper<Merchant> {

    public static final MerchantRowMapper INSTANCE = new MerchantRowMapper();

    public static final String COLUMNS = """
            id, public_id, business_name, business_type, rc_number, email, phone, address,
            status, api_key_hash, webhook_url, created_at, updated_at""";

    private MerchantRowMapper() {
    }

    @Override
    public Merchant mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Merchant.builder()
                .id(rs.getLong("id"))
                .publicId(rs.getObject("public_id", UUID.class))
                .businessName(rs.getString("business_name"))
                .businessType(enumValue(rs, "business_type", BusinessType.class))
                .rcNumber(rs.getString("rc_number"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .address(rs.getString("address"))
                .status(enumValue(rs, "status", MerchantStatus.class))
                .apiKeyHash(rs.getString("api_key_hash"))
                .webhookUrl(rs.getString("webhook_url"))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }
}
