package com.bytezone.dm3270.screen;

/*
 * O campo de comando do TSO: se a tela atual tem um, qual e, e o registro de cada comando
 * que o usuario mandou por ele.
 *
 * Consumidor unico: AIDCommand.process, que guarda o texto do campo quando o usuario
 * aperta ENTER numa tela de comando. E o assistente de TSO morando no contrato do
 * protocolo; o papel so da nome a isso.
 */
// -----------------------------------------------------------------------------------//
public interface TsoCommandTarget
// -----------------------------------------------------------------------------------//
{
  boolean isTSOCommandScreen ();

  Field getTSOCommandField ();

  void addTSOCommand (String command);
}
