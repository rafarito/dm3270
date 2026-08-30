package com.bytezone.dm3270.utilities;

/*
 * A configuracao de uma conexao: host, porta, modelo de terminal, SSL e a pasta onde os
 * arquivos transferidos sao gravados.
 *
 * Isto e uma PORTA, no sentido em que o resto desta refatoracao usa a palavra: um contrato
 * declarado onde ele e consumido, e implementado por quem tem os dados. Ate o commit
 * anterior, Site era a classe concreta feita de widgets JavaFX - hoje SiteForm -, e era ela
 * que atravessava filetransfer, streams, display e application. Cinco pacotes que nao
 * desenham nada carregavam, em tempo de compilacao, um objeto com nove TextField e CheckBox
 * dentro, e podiam alcancar qualquer um deles.
 *
 * Nenhum desses consumidores usava os widgets. A medicao que precedeu este passo mostrou que
 * os widgets do Site eram lidos por uma classe so, o SiteListStage, que e quem monta o
 * formulario na cena e quem o salva nas Preferences.
 *
 * CONTRATO DE LEITURA, e ele nao e o obvio. Os acessores NAO sao puros por contrato. No
 * SiteForm, getPort () e getModel () corrigem o campo quando encontram valor invalido,
 * gravando "23" e "2" de volta no widget - e e assim que o valor corrigido chega as
 * Preferences quando o SiteListStage salva. Por isso quem chama le o valor do MOMENTO DA
 * CHAMADA, e nao o de quando recebeu o objeto: o TransferMenu chama getFolder () no instante
 * em que o usuario aciona o menu, e o TransferManager so o chama quando um IND$FILE aparece.
 *
 * Essa propriedade tem teste dedicado no SiteFormTest, e foi ela que descartou o record
 * imutavel que o §7.3 do relatorio previa: um valor tirado no getSelectedSite () congelaria
 * a porta antes da correcao, e o que fica gravado no disco passaria a ser outro.
 */
// -----------------------------------------------------------------------------------//
public interface Site
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  String getName ();

  String getURL ();

  int getPort ();

  boolean getExtended ();

  int getModel ();

  boolean getPlugins ();

  boolean getSsl ();

  boolean getTrustAll ();

  String getFolder ();
}
