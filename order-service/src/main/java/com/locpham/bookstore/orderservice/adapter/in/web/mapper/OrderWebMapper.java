package com.locpham.bookstore.orderservice.adapter.in.web.mapper;

import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import com.locpham.bookstore.orderservice.application.command.SubmitOrderCommand;
import com.locpham.bookstore.orderservice.application.query.GetOrdersQuery;

public class OrderWebMapper {
    public static SubmitOrderCommand toCommand(OrderRequest request, String userId) {
        return new SubmitOrderCommand(request.isbn(), request.quantity(), userId);
    }

    public static GetOrdersQuery toQuery(String userId) {
        return new GetOrdersQuery(userId);
    }
}
