/**
 * Shared web infrastructure: the single place that renders API errors as RFC
 * 9457 Problem Details for every module ({@link
 * com.fulfillflow.platform_api.web.ApiExceptionHandler}). It depends on no
 * module and is a cross-cutting technical module, so it is declared
 * {@link org.springframework.modulith.ApplicationModule.Type#OPEN}.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.fulfillflow.platform_api.web;
