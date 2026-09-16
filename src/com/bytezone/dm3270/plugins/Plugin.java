package com.bytezone.dm3270.plugins;

/*
 * A interface que todo plugin implementa, e que o host usa para descobri-lo.
 *
 * Os seis metodos continuam aqui e continuam TODOS default - e isso e o que mantem os JARs
 * de terceiros ja compilados carregando (Regra 3). O que mudou no passo 9 foi so o extends:
 * os seis foram agrupados nos tres PAPEIS que o host de fato usa separados, e Plugin estende
 * os tres. Acrescentar superinterface e compativel no binario pela JLS 13.5.3, desde que
 * nenhum metodo abstrato entre - e nenhum entra, porque os defaults ficam aqui.
 *
 * O ganho e do lado do HOST, que era quem sofria: processAll passa a nomear AutoPlugin,
 * select () passa a nomear Activatable, e o caminho de request passa a nomear RequestPlugin -
 * dois metodos por sitio em vez de seis. Do lado de quem escreve um plugin nada muda: a
 * descoberta filtra por Plugin, entao e Plugin que se implementa, e os papeis servem como
 * documentacao do que cada par de metodos significa.
 *
 * E O DESPACHO CONTINUA PERGUNTANDO, nunca testando o tipo. doesAuto () e doesRequest ()
 * mudam durante a execucao - 39 e 10 transicoes vivas, medidas nos seis plugins reais -,
 * entao um instanceof responderia uma pergunta estatica no lugar de uma que muda a cada tela.
 * Como todo Plugin e um AutoPlugin e um RequestPlugin, esse instanceof e trivialmente
 * verdadeiro e nao filtra nada: o erro fica estruturalmente impossivel, em vez de so
 * desaconselhado.
 */
// -----------------------------------------------------------------------------------//
public interface Plugin extends Activatable, AutoPlugin, RequestPlugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Override
  default void activate ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  default void deactivate ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  default boolean doesAuto ()
  // ---------------------------------------------------------------------------------//
  {
    return false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  default boolean doesRequest ()
  // ---------------------------------------------------------------------------------//
  {
    return false;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  default void processAuto (PluginData screen)
  // ---------------------------------------------------------------------------------//
  {
  }

  // ---------------------------------------------------------------------------------//
  @Override
  default void processRequest (PluginData screen)
  // ---------------------------------------------------------------------------------//
  {
  }
}