package com.bytezone.dm3270.plugins;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * O digest MD5 e a conversao para hexadecimal que o DefaultPlugin entregava por heranca.
 *
 * Saiu de la no passo 9 por uma razao simples: um resumo criptografico de um vetor de bytes
 * nao tem nada a ver com plugin nenhum, e o proprio getMD5 nunca tocou num PluginData. Era
 * utilitario preso dentro de uma superclasse, e o preco de alcanca-lo era o unico slot de
 * heranca de quem quisesse usa-lo.
 *
 * O DefaultPlugin continua com os dois metodos, com assinatura e modificador intactos,
 * delegando - eles sao API de terceiro e os JARs ja compilados os alcancam.
 *
 * O LOGGER CONTINUA SENDO O DO DefaultPlugin, de proposito. O logback.xml imprime
 * %logger{36}, entao o nome do logger e saida observavel: mover a classe mudaria toda linha
 * que este erro produz. E a mesma regra que fez DatasetDetails declarar o logger de
 * ScreenWatcher e as seis classes novas de database declararem o de DatabaseThread.
 */
// -----------------------------------------------------------------------------------//
public final class PluginDigest
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DefaultPlugin.class);

  // ---------------------------------------------------------------------------------//
  private PluginDigest ()
  // ---------------------------------------------------------------------------------//
  {
  }

  /*
   * Devolve a string vazia quando o algoritmo nao existe, em vez de lancar. E o que o codigo
   * sempre fez, e na pratica MD5 nunca falta numa JVM - o ramo existe porque a API declara a
   * excecao.
   */
  // ---------------------------------------------------------------------------------//
  public static String md5 (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      byte[] digest = MessageDigest.getInstance ("MD5").digest (buffer);
      return toHex (digest);
    }
    catch (NoSuchAlgorithmException e)
    {
      logger.error ("NoSuchAlgorithmException in getMD5", e);
    }
    return "";
  }

  /*
   * Dois digitos por byte, A-F em MAIUSCULAS, tratando o byte como sem sinal.
   * Integer.toHexString nao serve: comeria o zero a esquerda do 0x0F e escreveria minusculas.
   */
  // ---------------------------------------------------------------------------------//
  public static String toHex (byte[] bytes)
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder builder = new StringBuilder (bytes.length * 2);
    for (int i = 0; i < bytes.length; i++)
    {
      int digit = (bytes[i] >> 4) & 0xF;
      builder.append (digit < 10 ? (char) ('0' + digit) : (char) ('A' - 10 + digit));
      digit = (bytes[i] & 0xF);
      builder.append (digit < 10 ? (char) ('0' + digit) : (char) ('A' - 10 + digit));
    }
    return builder.toString ();
  }
}
