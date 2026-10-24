package io.vobs.model;

import java.util.Objects;

/**
 * Sản phẩm ngân hàng. Tương ứng service domain <em>Product Directory</em> của BIAN.
 *
 * @param productId mã sản phẩm
 * @param name tên sản phẩm
 * @param type nhóm sản phẩm
 */
public record Product(String productId, String name, ProductType type) {

    public enum ProductType { CURRENT_ACCOUNT, SAVINGS_ACCOUNT, TERM_DEPOSIT, LOAN, CREDIT_CARD, DEBIT_CARD }

    public Product {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
