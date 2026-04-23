package local.xrechnung.saxonoutputverifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfileRegistry {

  static final String DEFAULT_PROFILE_ID = "xrechnung-ubl-invoice";

  private final Map<String, ExpectedProfile> profiles;

  private ProfileRegistry(Map<String, ExpectedProfile> profiles) {
    this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, ExpectedProfile>(profiles));
  }

  static ProfileRegistry builtInProfiles() {
    Map<String, ExpectedProfile> profiles = new LinkedHashMap<String, ExpectedProfile>();
    ExpectedProfile xrechnungUblInvoice = ExpectedProfile.xrechnungUblInvoice();
    profiles.put(xrechnungUblInvoice.getId(), xrechnungUblInvoice);
    return new ProfileRegistry(profiles);
  }

  ExpectedProfile require(String profileId) {
    ExpectedProfile profile = profiles.get(profileId);
    if (profile != null) {
      return profile;
    }
    throw new IllegalArgumentException(
        "Unknown profile: " + profileId + ". Available profiles: " + describeAvailableProfiles());
  }

  List<String> getProfileIds() {
    return Collections.unmodifiableList(new ArrayList<String>(profiles.keySet()));
  }

  static String describeAvailableProfiles() {
    return join(builtInProfiles().getProfileIds());
  }

  private static String join(List<String> values) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        builder.append(", ");
      }
      builder.append(values.get(index));
    }
    return builder.toString();
  }
}
