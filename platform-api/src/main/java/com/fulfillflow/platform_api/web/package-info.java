/**
 * Shared web infrastructure: the single place that renders API errors as RFC
 * 9457 Problem Details for every module ({@link ApiExceptionHandler}). It
 * depends on no module and is a cross-cutting technical module, so it is
 * declared {@link ApplicationModule.Type#OPEN}.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.fulfillflow.platform_api.web;

import org.springframework.modulith.ApplicationModule;
