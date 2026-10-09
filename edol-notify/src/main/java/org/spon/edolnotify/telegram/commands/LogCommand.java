package org.spon.edolnotify.telegram.commands;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.enums.ParseMode;
import io.github.natanimn.telebof.types.updates.Message;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.spon.edol.deployment.DeploymentMode;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component("log")
@RequiredArgsConstructor
public class LogCommand implements Command {

    private final DeploymentMode deploymentMode;

    @Override
    @SneakyThrows
    public void runCommand(BotContext ctx, Message message) {
        long chatId = message.getChat().getId();
        if (deploymentMode == DeploymentMode.SECURE_MULTI_TENANT) {
            ctx.sendMessage(chatId, "This command is unavailable in secure multi-tenant mode.").exec();
            return;
        }

        Path logPath = Paths.get("./logs/edol_core_application.log");
        List<String> arguments = getArguments(message);
        int n = !arguments.isEmpty() ? Integer.parseInt(arguments.get(0)) : 15; // Number of last lines to read
        if (n > 15) {
            ctx.sendMessage(message.getChat().getId(), "Max log lines is 15!").exec();
            return;
        }

        List<String> result = new ArrayList<>();
        List<String> allLines = Files.readAllLines(logPath);
        int start = Math.max(0, allLines.size() - n);
        for (int i = start; i < allLines.size(); i++) {
            result.add(allLines.get(i));
        }

        String logs = "```Logs: " + String.join("\r\n", result) + "```";

        ctx.sendMessage(message.getChat().getId(), logs)
                .parseMode(ParseMode.MARKDOWNV2)
                .exec();
    }

}
