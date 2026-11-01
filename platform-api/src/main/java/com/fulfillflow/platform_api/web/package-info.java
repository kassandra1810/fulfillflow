/**
 * Shared web infrastructure: the single place that renders API errors as RFC
 * 9457 Problem Details for every module ({@link ApiExceptionHandler}). It is a
 * regular application module that depends on no other module; it has no
 * internal subpackages, so every module may use it without being declared open.
 */
package com.fulfillflow.platform_api.web;
