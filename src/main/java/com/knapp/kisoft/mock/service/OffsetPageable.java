package com.knapp.kisoft.mock.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} that pages by an absolute {@code offset}/{@code limit} rather than a page index, so
 * OData {@code $skip}/{@code $top} translate directly into a database {@code OFFSET/LIMIT}. This keeps the
 * mock read endpoints from materialising an entire table into memory before paging (see the OData GETs in
 * StockReportController / GoodsOutOrderController).
 */
public final class OffsetPageable implements Pageable {

    private final long offset;
    private final int limit;
    private final Sort sort;

    public OffsetPageable(long offset, int limit, Sort sort) {
        this.offset = Math.max(0, offset);
        this.limit = Math.max(1, limit);
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / limit);
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageable(offset - limit, limit, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
