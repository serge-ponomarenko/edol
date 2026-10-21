package org.spon.edolhub.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = PrinterManagementController.class)
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-hub.agent.enabled:false}")
public class AgentManagementAvailability {
    @ModelAttribute("agentManagementAvailable")
    public boolean available() {
        return true;
    }
}
