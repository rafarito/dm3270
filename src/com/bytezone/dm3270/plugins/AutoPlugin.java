package com.bytezone.dm3270.plugins;

/*
 * O papel de quem reage a CADA tela que chega do mainframe.
 *
 * LEIA ISTO ANTES DE MEXER NO DESPACHO, porque e o fato mais surpreendente desta API e ate o
 * passo 9 ele nao estava escrito em lugar nenhum:
 *
 *   doesAuto () E PERGUNTADO A CADA TELA, e nao uma vez.
 *
 * Nao e uma propriedade do plugin, e sim um estado que ele muda enquanto trabalha. Medido nos
 * seis plugins do repositorio irmao: 39 transicoes vivas em cinco deles. No FanLogon o
 * vaivem E a maquina de estados do logon - processRequest liga o campo quando reconhece a
 * tela de entrada, e processAuto o desliga nas nove saidas possiveis do laco.
 *
 * Por isso NAO EXISTE despacho por instanceof aqui, e implementar esta interface nao quer
 * dizer "este plugin e automatico": quer dizer "este plugin sabe responder se, agora, ele
 * quer a proxima tela". Quem decide e o metodo, sempre, a cada passo.
 *
 * Como Plugin estende AutoPlugin, todo plugin que ja existiu e um AutoPlugin - entao um
 * "instanceof AutoPlugin" e trivialmente verdadeiro e nao serve de filtro para nada. Isso e
 * de proposito: torna estruturalmente impossivel reintroduzir o despacho estatico que a
 * medicao descartou.
 */
// -----------------------------------------------------------------------------------//
public interface AutoPlugin
// -----------------------------------------------------------------------------------//
{
  boolean doesAuto ();

  void processAuto (PluginData data);
}
