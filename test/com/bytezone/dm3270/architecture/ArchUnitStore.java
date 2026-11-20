package com.bytezone.dm3270.architecture;

import com.tngtech.archunit.ArchConfiguration;

// Configura o baseline em test/archunit-baseline via codigo porque o pom nao declara
// <testResources> (um archunit.properties nao iria para o classpath de teste).
// -----------------------------------------------------------------------------------//
final class ArchUnitStore
// -----------------------------------------------------------------------------------//
{
  private static final String STORE_PATH = "test/archunit-baseline";

  // ---------------------------------------------------------------------------------//
  private ArchUnitStore ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  static void configure ()
  // ---------------------------------------------------------------------------------//
  {
    ArchConfiguration configuration = ArchConfiguration.get ();

    configuration.setProperty ("freeze.store.default.path", STORE_PATH);
    configuration.setProperty ("freeze.store.default.allowStoreCreation", "true");
    configuration.setProperty ("freeze.refreeze", refreeze ());
  }

  // Use -Darchunit.freeze.refreeze=true apenas quando uma assinatura mudar e reescrever
  // uma violacao ja existente no baseline (para revisar o diff manualmente).
  // ---------------------------------------------------------------------------------//
  private static String refreeze ()
  // ---------------------------------------------------------------------------------//
  {
    return Boolean.getBoolean ("archunit.freeze.refreeze") ? "true" : "false";
  }
}
