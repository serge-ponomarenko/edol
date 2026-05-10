package org.spon.edolnotify.service;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.Filament;
import org.spon.edol.model.PrinterState;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramMessageFormatterService {

    private final PrinterService printerService;

    public String buildStatusMessage() {
        PrinterState state = printerService.getState();

        StringBuilder msg = new StringBuilder();

        if (state.getError() != null) {
            msg.append("\uD83D\uDED1 Printer Error!\n")
                    .append("Code: ").append(state.getError().getCode()).append("\n")
                    .append(state.getError().getMessage()).append("\n\n");
        }

        msg.append(formatState(state)).append(" | ")
                .append(state.getProgress()).append("%").append(" | ")
                .append(formatMinutes(state.getRemainingTime()))
                .append("\n");

        msg.append("🖨 <b>")
                .append(state.getCurrentTask() == null || state.getCurrentTask().isEmpty() ?
                        "[no task loaded]" : state.getCurrentTask())
                .append("</b>\n\n");

        appendFilaments(msg, state);

        appendAMS(msg, state);

        return msg.toString();
    }

    private String formatState(PrinterState state) {
        if ("RUNNING".equals(state.getGcodeState()))
            return "🟢 Printing";

        if ("PAUSE".equals(state.getGcodeState()))
            return "⏸️ Paused";

        if ("IDLE".equals(state.getGcodeState()))
            return "⚪ Idle";

        if ("PREPARE".equals(state.getGcodeState()))
            return "\uD83D\uDEE0\uFE0F Preparing";

        if ("FAILED".equals(state.getGcodeState()))
            return "\uD83D\uDD34 Failed";

        if ("FINISH".equals(state.getGcodeState()))
            return "\uD83C\uDFC1 Finish";

        return state.getGcodeState() == null ? "⚪ Idle" : state.getGcodeState();
    }

    private void appendFilaments(StringBuilder msg, PrinterState state) {
        if (state.getFilaments() == null || state.getFilaments().isEmpty())
            return;

        msg.append("🧵 Filament\n");

        for (Filament f : state.getFilaments()) {

            msg.append(colorEmoji(f.getColor()))
                    .append(" ");

            if (state.isExternalSpoolUsed()) {
                msg.append("(EXT) ");
            } else {
                Integer amsSlot = f.getAmsSlot();
                if (amsSlot != null) {
                    msg.append("(AMS ").append(amsSlot + 1).append(") ");
                }
            }

            msg.append(f.getType());

            if (f.getVendor() != null)
                msg.append(" (").append(f.getVendor()).append(")");

            msg.append(" - ")
                    .append(round(f.getUsedGrams()))
                    .append(" g\n");
        }
    }

    private void appendAMS(StringBuilder msg, PrinterState state) {
        if (state.getAms() == null)
            return;

        msg.append("\n📦 AMS\n");

        msg.append("Humidity: ")
                .append(state.getAms().getHumidity()).append("* (")
                .append(state.getAms().getHumidityRaw()).append("%)")
                .append("\n");

        msg.append("Temp: ")
                .append(round(state.getAms().getTemperature()))
                .append(" °C\n");

        msg.append("Active slot: ")
                .append(state.getAms().getActiveSlot() + 1)
                .append(state.getAms().getActiveSlot() >= 254 ? " (EXT)" : "");
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;

        if (h == 0)
            return m + " min";

        return h + "h " + m + "m";
    }

    private int round(double v) {
        return (int) Math.round(v);
    }

    private String colorEmoji(String hex) {
        if (hex == null)
            return "⚪";

        hex = hex.toUpperCase();

        if (hex.contains("FF6A13"))
            return "🟠";

        if (hex.contains("00AE42"))
            return "🟢";

        if (hex.contains("000000") || hex.contains("161616"))
            return "⚫";

        if (hex.contains("FFFFFF"))
            return "⚪";

        return "🎨";
    }
}