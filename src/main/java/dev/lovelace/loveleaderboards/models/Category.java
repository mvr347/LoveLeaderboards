package dev.lovelace.loveleaderboards.models;

public record Category(
    String name,
    String displayName,
    String description,
    boolean enabled,
    int sortOrder,
    String timePeriodEnabled,
    String integration
) {
    public boolean isAllTimeEnabled() {
        return timePeriodEnabled.equalsIgnoreCase("alltime") || timePeriodEnabled.equalsIgnoreCase("both");
    }

    public boolean isMonthlyEnabled() {
        return timePeriodEnabled.equalsIgnoreCase("monthly") || timePeriodEnabled.equalsIgnoreCase("both");
    }

    public String getEntityType() {
        if ("LoveClans".equalsIgnoreCase(integration) || name.toLowerCase().contains("clan")) {
            return "clan";
        }
        return "player";
    }

    public boolean isClanCategory() {
        return "clan".equalsIgnoreCase(getEntityType());
    }

    public String getScoreUnit() {
        if (isClanCategory()) return "Мощь";
        return switch (name.toLowerCase()) {
            case "kills" -> "Убийств";
            case "bounty-completed" -> "Бонусов";
            default -> "Очки";
        };
    }
}

