package com.bytezone.dm3270.utilities;

/*
 * Um Site que e so os valores: sem widget, sem toolkit, sem efeito colateral na leitura.
 *
 * Existe para quem conhece a configuracao no momento em que a escreve, e nao precisa de um
 * formulario para guarda-la. O primeiro caso e o Console.DEFAULT_MAINFRAME, o destino fixo
 * do modo Test - "mainframe" em localhost:5555 -, que era construido como SiteForm e
 * portanto exigia o toolkit JavaFX so para inicializar a classe Console. Um teste headless
 * que precise de um Site tambem pode usar este.
 *
 * A DIFERENCA DE CONTRATO EM RELACAO AO SiteForm, e ela e proposital. O formulario le o
 * widget a cada chamada, e getPort () e getModel () corrigem o campo quando encontram valor
 * invalido. Aqui nao ha o que corrigir: os valores entram pelo construtor e nao mudam, entao
 * os acessores sao puros. As duas implementacoes cumprem o mesmo contrato de leitura porque
 * a interface promete o valor do momento da chamada - e num valor imutavel esse e sempre o
 * mesmo.
 *
 * O toString repete o formato do SiteForm, e nao o que o record geraria, para que os dois
 * sejam intercambiaveis tambem quando alguem os imprime.
 */
// -----------------------------------------------------------------------------------//
public record SiteValue (String name, String url, int port, boolean extended, int model,
    boolean plugins, boolean ssl, boolean trustAll, String folder) implements Site
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  public String getName ()
  // ---------------------------------------------------------------------------------//
  {
    return name;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getURL ()
  // ---------------------------------------------------------------------------------//
  {
    return url;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int getPort ()
  // ---------------------------------------------------------------------------------//
  {
    return port;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getExtended ()
  // ---------------------------------------------------------------------------------//
  {
    return extended;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public int getModel ()
  // ---------------------------------------------------------------------------------//
  {
    return model;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getPlugins ()
  // ---------------------------------------------------------------------------------//
  {
    return plugins;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getSsl ()
  // ---------------------------------------------------------------------------------//
  {
    return ssl;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public boolean getTrustAll ()
  // ---------------------------------------------------------------------------------//
  {
    return trustAll;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String getFolder ()
  // ---------------------------------------------------------------------------------//
  {
    return folder;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("Site [name=%s, url=%s, port=%d, folder=%s]", name, url, port,
        folder);
  }
}
