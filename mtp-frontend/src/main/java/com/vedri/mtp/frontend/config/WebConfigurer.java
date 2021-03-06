package com.vedri.mtp.frontend.config;

import java.util.Arrays;
import java.util.EnumSet;

import javax.servlet.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.MimeMappings;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlet.InstrumentedFilter;
import com.codahale.metrics.servlets.MetricsServlet;
import com.vedri.mtp.core.MtpConstants;
import com.vedri.mtp.frontend.web.filter.CachingHttpHeadersFilter;
import com.vedri.mtp.frontend.web.filter.StaticResourcesProductionFilter;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Slf4j
@Configuration
public class WebConfigurer implements ServletContextInitializer, EmbeddedServletContainerCustomizer {

	@Autowired
	private Environment env;

	@Autowired(required = false)
	private MetricRegistry metricRegistry;

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		log.info("Web application configuration, using profiles: {}", Arrays.toString(env.getActiveProfiles()));
		EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD,
				DispatcherType.ASYNC);
		initMetrics(servletContext, disps);
		if (env.acceptsProfiles(MtpConstants.SPRING_PROFILE_PRODUCTION)) {
			initCachingHttpHeadersFilter(servletContext, disps);
			initStaticResourcesProductionFilter(servletContext, disps);
		}
		log.info("Web application fully configured");
	}

	/**
	 * Set up Mime types.
	 */
	@Override
	public void customize(ConfigurableEmbeddedServletContainer container) {
		MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
		// IE issue, see https://github.com/jhipster/generator-jhipster/pull/711
		mappings.add("html", "text/html;charset=utf-8");
		// CloudFoundry issue, see https://github.com/cloudfoundry/gorouter/issues/64
		mappings.add("json", "text/html;charset=utf-8");
		container.setMimeMappings(mappings);
	}

	/**
	 * Initializes the static resources production Filter.
	 */
	private void initStaticResourcesProductionFilter(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {

		log.debug("Registering static resources production Filter");
		FilterRegistration.Dynamic staticResourcesProductionFilter = servletContext.addFilter(
				"staticResourcesProductionFilter",
				new StaticResourcesProductionFilter());

		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true, "/");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true, "/index.html");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true, "/assets/*");
		staticResourcesProductionFilter.addMappingForUrlPatterns(disps, true, "/scripts/*");
		staticResourcesProductionFilter.setAsyncSupported(true);
	}

	/**
	 * Initializes the caching HTTP Headers Filter.
	 */
	private void initCachingHttpHeadersFilter(ServletContext servletContext,
			EnumSet<DispatcherType> disps) {
		log.debug("Registering Caching HTTP Headers Filter");
		FilterRegistration.Dynamic cachingHttpHeadersFilter = servletContext.addFilter("cachingHttpHeadersFilter",
				new CachingHttpHeadersFilter(env));

		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "/assets/*");
		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "/scripts/*");
		cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "/maps/*");
		cachingHttpHeadersFilter.setAsyncSupported(true);
	}

	/**
	 * Initializes Metrics.
	 */
	private void initMetrics(ServletContext servletContext, EnumSet<DispatcherType> disps) {
		log.debug("Initializing Metrics registries");
		servletContext.setAttribute(InstrumentedFilter.REGISTRY_ATTRIBUTE,
				metricRegistry);
		servletContext.setAttribute(MetricsServlet.METRICS_REGISTRY,
				metricRegistry);

		log.debug("Registering Metrics Filter");
		FilterRegistration.Dynamic metricsFilter = servletContext.addFilter("webappMetricsFilter",
				new InstrumentedFilter());

		metricsFilter.addMappingForUrlPatterns(disps, true, "/*");
		metricsFilter.setAsyncSupported(true);

		log.debug("Registering Metrics Servlet");
		ServletRegistration.Dynamic metricsAdminServlet = servletContext.addServlet("metricsServlet",
				new MetricsServlet());

		metricsAdminServlet.addMapping("/metrics/metrics/*");
		metricsAdminServlet.setAsyncSupported(true);
		metricsAdminServlet.setLoadOnStartup(2);
	}
}
