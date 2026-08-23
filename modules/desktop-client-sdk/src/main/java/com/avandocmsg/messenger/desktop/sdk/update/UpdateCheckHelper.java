package com.avandocmsg.messenger.desktop.sdk.update;

import com.avandocmsg.messenger.desktop.sdk.api.KorusApiClient;

/** UI-friendly update check without exposing OkHttp types to JavaFX module. */
public final class UpdateCheckHelper {

  public record Result(boolean updateAvailable, String latestVersion, String message) {}

  private UpdateCheckHelper() {}

  public static Result check(String feedUrl, String currentVersion) {
    try {
      var service = new UpdateService(KorusApiClient.defaultHttpClient());
      var result = service.checkForUpdate(
          feedUrl,
          currentVersion,
          DesktopVersions.platformKey(),
          null,
          null
      );
      if (result.updateAvailable()) {
        return new Result(
            true,
            result.latestVersion(),
            "Доступно: " + result.latestVersion() + " (" + result.artifact().platform() + ")"
        );
      }
      return new Result(false, currentVersion, "Актуальная версия: " + currentVersion);
    } catch (Exception e) {
      return new Result(false, currentVersion, "Ошибка: " + e.getMessage());
    }
  }
}
