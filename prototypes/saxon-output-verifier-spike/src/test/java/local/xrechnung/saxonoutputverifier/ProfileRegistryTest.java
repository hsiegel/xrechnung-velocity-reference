package local.xrechnung.saxonoutputverifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class ProfileRegistryTest {

  @Test
  public void resolvesBuiltInProfile() {
    ProfileRegistry registry = ProfileRegistry.builtInProfiles();

    ExpectedProfile profile = registry.require(ProfileRegistry.DEFAULT_PROFILE_ID);

    assertEquals(ProfileRegistry.DEFAULT_PROFILE_ID, profile.getId());
    assertEquals(ScenarioCatalog.INVOICE_SCENARIO_NAME, profile.getScenarioName());
  }

  @Test
  public void rejectsUnknownProfileWithAvailableIds() {
    ProfileRegistry registry = ProfileRegistry.builtInProfiles();

    IllegalArgumentException error =
        Assert.assertThrows(IllegalArgumentException.class, () -> registry.require("unknown"));

    assertTrue(error.getMessage().contains("Unknown profile"));
    assertTrue(error.getMessage().contains(ProfileRegistry.DEFAULT_PROFILE_ID));
  }
}
