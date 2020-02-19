package org.jetlinks.community.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.hswebframework.web.utils.ModuleUtils;
import org.jetlinks.community.logging.system.SerializableSystemLog;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SystemLoggingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    public static ApplicationEventPublisher publisher;

    public static final Map<String, String> staticContext = new ConcurrentHashMap<>();

    @Override
    protected void append(ILoggingEvent event) {

        if (publisher == null) {
            return;
        }

        StackTraceElement element = event.getCallerData()[0];
        IThrowableProxy proxies = event.getThrowableProxy();
        String message = event.getFormattedMessage();
        String stack = null;
        if (null != proxies) {
            int commonFrames = proxies.getCommonFrames();
            StackTraceElementProxy[] stepArray = proxies.getStackTraceElementProxyArray();
            StringJoiner joiner = new StringJoiner("\n", message + "\n[", "]");
            StringBuilder stringBuilder = new StringBuilder();
            ThrowableProxyUtil.subjoinFirstLine(stringBuilder, proxies);
            joiner.add(stringBuilder);
            for (int i = 0; i < stepArray.length - commonFrames; i++) {
                StringBuilder sb = new StringBuilder();
                sb.append(CoreConstants.TAB);
                ThrowableProxyUtil.subjoinSTEP(sb, stepArray[i]);
                joiner.add(sb);
            }
            stack = joiner.toString();
        }


        try {
            String gitLocation = null;
            String mavenModule = null;
            try {
                Class clazz = Class.forName(element.getClassName());
                ModuleUtils.ModuleInfo moduleInfo = ModuleUtils.getModuleByClass(clazz);
                if (!StringUtils.isEmpty(moduleInfo.getGitRepository())) {
                    StringBuilder javaSb = new StringBuilder();
                    javaSb.append(moduleInfo.getGitLocation());
                    javaSb.append("src/main/java/");
                    javaSb.append((ClassUtils.getPackageName(Class.forName(element.getClassName())).replace(".", "/")));
                    javaSb.append("/");
                    javaSb.append(Class.forName(element.getClassName()).getSimpleName());
                    javaSb.append(".java#L");
                    javaSb.append(element.getLineNumber());
                    gitLocation = javaSb.toString();
                }
                mavenModule = moduleInfo.getArtifactId();
            } catch (Exception e) {
                log.warn("记录系统日志时，加载类:{}错误。{}", element.getClassName(), e);
            }
            Map<String, String> context = new HashMap<>(staticContext);
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            if (mdc != null) {
                context.putAll(mdc);
            }
            SerializableSystemLog info = SerializableSystemLog.builder()
                    .id(IDGenerator.SNOW_FLAKE_STRING.generate())
                    .mavenModule(mavenModule)
                    .context(context)
                    .name(event.getLoggerName())
                    .level(event.getLevel().levelStr)
                    .className(element.getClassName())
                    .methodName(element.getMethodName())
                    .lineNumber(element.getLineNumber())
                    .exceptionStack(stack)
                    .java(gitLocation)
                    .threadName(event.getThreadName())
                    .createTime(event.getTimeStamp())
                    .message(message)
                    .threadId(String.valueOf(Thread.currentThread().getId()))
                    .build();
            try {
                publisher.publishEvent(info);
            }catch (Exception ignore){}
        } catch (Exception e) {
            log.error("组装系统日志错误", e);
        }

    }
}
