package com.example.certmonitor.web;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.service.CertificateNotFoundException;
import com.example.certmonitor.service.ExpiryStatusCalculator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ExpiryStatusCalculator calculator;
    private final AppProperties appProperties;

    public GlobalExceptionHandler(ExpiryStatusCalculator calculator, AppProperties appProperties) {
        this.calculator = calculator;
        this.appProperties = appProperties;
    }

    @ExceptionHandler({CertificateNotFoundException.class,
            MethodArgumentTypeMismatchException.class,
            NoResourceFoundException.class})
    public ModelAndView handleNotFound(Exception exception, HttpServletRequest request) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), exception.getMessage());
        ModelAndView mav = errorView("error", HttpStatus.NOT_FOUND, request);
        mav.addObject("error", "Not found");
        mav.addObject("message", "The requested resource could not be found.");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error while processing {}", request.getRequestURI(), exception);
        ModelAndView mav = errorView("error", HttpStatus.INTERNAL_SERVER_ERROR, request);
        mav.addObject("error", "Something went wrong");
        mav.addObject("message", "The request could not be completed. Please try again or contact the administrator.");
        return mav;
    }

    private ModelAndView errorView(String view, HttpStatus status, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView(view, status);
        mav.addObject("status", status.value());
        mav.addObject("path", request.getRequestURI());
        mav.addObject("warningThresholdDays", calculator.warningThresholdDays());
        mav.addObject("demoEnabled", appProperties.getDemoData().isEnabled());
        return mav;
    }
}
