package com.bytezone.dm3270.plugins;

/*
 * O que o host chama quando o usuario liga ou desliga um plugin no Plugin Manager.
 *
 * Um dos tres papeis em que a interface Plugin foi segregada. Plugin estende os tres e
 * mantem um default vazio para cada metodo, entao nenhum plugin - nem os ja compilados -
 * precisa mudar; o que muda e o HOST, que passa a nomear so o papel que usa em cada sitio.
 *
 * O par nao e simetrico na pratica: PluginEntry.select () chama activate () ou deactivate ()
 * e SO DEPOIS pergunta doesRequest (), o que e o que permite a um plugin armar as proprias
 * capacidades dentro do activate (). O FanLogon faz exatamente isso.
 */
// -----------------------------------------------------------------------------------//
public interface Activatable
// -----------------------------------------------------------------------------------//
{
  void activate ();

  void deactivate ();
}
