package org.spon.edolhub.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.spon.edolhub.service.CoreAgentManagementClient;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Controller
@RequestMapping("/printers/{printerId}/agent")
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-hub.agent.enabled:false}")
public class AgentManagementController {
    private static final String AGENT_ERROR = "agentError";
    private static final String PAGE = "dashboard/printers/agent";
    private static final String STATUS = "agentStatus";
    private final TenantOwnerAuthorizationService owners;
    private final PrinterAccessService printers;
    private final CoreAgentManagementClient agents;

    public AgentManagementController(TenantOwnerAuthorizationService owners, PrinterAccessService printers,
                                     CoreAgentManagementClient agents) {
        this.owners = owners;
        this.printers = printers;
        this.agents = agents;
    }

    @GetMapping
    public String page(@PathVariable UUID printerId, Model model, HttpServletResponse response) {
        authorize(printerId, model, response);
        model.addAttribute(STATUS, agents.status(printerId));
        return PAGE;
    }

    @PostMapping("/pairings")
    public String pair(@PathVariable UUID printerId, Model model, HttpServletResponse response) {
        return pairing(printerId, false, model, response);
    }

    @PostMapping("/rotate")
    public String rotate(@PathVariable UUID printerId, Model model, HttpServletResponse response) {
        return pairing(printerId, true, model, response);
    }

    @PostMapping("/revoke")
    public String revoke(@PathVariable UUID printerId, Model model, HttpServletResponse response) {
        authorize(printerId, model, response);
        try {
            agents.revoke(printerId);
        } catch (HttpServerErrorException.ServiceUnavailable exception) {
            response.setStatus(503);
            model.addAttribute(AGENT_ERROR, "Access is revoked in Core. MQTT disconnection is pending; retry when the broker is available.");
        }
        model.addAttribute(STATUS, agents.status(printerId));
        return PAGE;
    }

    private String pairing(UUID printerId, boolean rotate, Model model, HttpServletResponse response) {
        authorize(printerId, model, response);
        try {
            model.addAttribute("agentPairing", agents.pair(printerId, rotate));
        } catch (HttpClientErrorException exception) {
            if (!exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw exception;
            }
            response.setStatus(409);
            model.addAttribute(AGENT_ERROR, "An active agent or unexpired pairing already exists.");
        } catch (HttpServerErrorException.ServiceUnavailable exception) {
            response.setStatus(503);
            model.addAttribute(AGENT_ERROR, "MQTT disconnection is pending. No replacement code was issued; retry when the broker is available.");
        }
        model.addAttribute(STATUS, agents.status(printerId));
        return PAGE;
    }

    private void authorize(UUID printerId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        owners.requireCurrentOwner();
        try {
            printers.getPrinter(printerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("agentPrinterId", printerId);
    }
}
