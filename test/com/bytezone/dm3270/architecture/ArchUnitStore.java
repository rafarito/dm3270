package com.bytezone.dm3270.architecture;

import com.tngtech.archunit.ArchConfiguration;

/*
 * Aponta o baseline congelado do ArchUnit para test/archunit-baseline.
 *
 * Por padrao o FreezingArchRule grava num diretorio archunit_store solto na raiz do
 * projeto, que ninguem versiona por acidente. O baseline precisa ser versionado: e ele que
 * distingue "violacao que ja existia" de "violacao nova", e o diff dele e o placar da
 * refatoracao.
 *
 * A configuracao e feita por codigo, e nao por um archunit.properties, porque este projeto
 * nao declara <testResources> no pom - o diretorio test so publica as classes compiladas.
 */
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

    // O baseline so encolhe sozinho. Ele nunca ganha entradas por conta propria: uma
    // violacao nova tem de quebrar a build para ser vista.
    configuration.setProperty ("freeze.refreeze", "false");
  }
}
