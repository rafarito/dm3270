package com.bytezone.dm3270.streams;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

// -----------------------------------------------------------------------------------//
public class SslContextFactory
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  /** Cria SSLContext usando o TrustStore padrão da JVM (cacerts). */
  public static SSLContext createDefault () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    SSLContext ctx = SSLContext.getInstance ("TLS");
    ctx.init (null, null, new SecureRandom ());
    return ctx;
  }

  // ---------------------------------------------------------------------------------//
  /**
   * Cria SSLContext que aceita qualquer certificado.
   * ATENÇÃO: Use apenas em desenvolvimento/testes!
   */
  public static SSLContext createTrustAll () throws Exception
  // ---------------------------------------------------------------------------------//
  {
    TrustManager[] trustAll = new TrustManager[]
    {
      new X509TrustManager ()
      {
        public X509Certificate[] getAcceptedIssuers ()                        { return new X509Certificate[0]; }
        public void checkClientTrusted (X509Certificate[] c, String a)        {}
        public void checkServerTrusted (X509Certificate[] c, String a)        {}
      }
    };

    SSLContext ctx = SSLContext.getInstance ("TLS");
    ctx.init (null, trustAll, new SecureRandom ());
    return ctx;
  }
}
