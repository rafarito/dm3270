package com.bytezone.dm3270.database;

/*
 * O aviso de que uma operacao no armazenamento terminou.
 *
 * O texto e o mesmo relatorio que DatabaseRequest.toString produzia - banco, comando,
 * resultado e se houve alteracao. Quem pediu a operacao decide o que fazer com ele: hoje o
 * unico interessado e o FieldManager, que o registra em nivel debug com o seu proprio logger.
 * Passar o texto pronto, em vez do objeto de request, e o que mantem DatabaseRequest fora das
 * camadas de cima.
 */
// -----------------------------------------------------------------------------------//
public interface StoreListener
// -----------------------------------------------------------------------------------//
{
  void storeCompleted (String report);
}
