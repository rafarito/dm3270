package com.bytezone.dm3270.plugins;

/*
 * O papel de quem responde a um pedido explicito do usuario, pelo menu de plugins.
 *
 * doesRequest () tem vida diferente do doesAuto (), e a diferenca e comportamento observavel:
 * ele e perguntado na montagem do menu, e o item criado fica TRAVADO - o guarda e
 * requestMenuItem == null, e depois que o item existe nem select () nem rebuildMenu ()
 * perguntam de novo. Um plugin pode ter deixado de querer pedidos e continuar com o item no
 * menu; o FanLogoff real zera o proprio doesRequest na linha 115 e mantem o item.
 *
 * Por isso aqui tambem nao ha despacho por instanceof: a resposta muda durante a execucao -
 * 10 transicoes vivas em cinco dos seis plugins - e a trava e o que o host de fato consulta.
 * Quem quiser entender a sequencia exata, ela esta congelada em PluginsStageDispatchTest.
 */
// -----------------------------------------------------------------------------------//
public interface RequestPlugin
// -----------------------------------------------------------------------------------//
{
  boolean doesRequest ();

  void processRequest (PluginData data);
}
