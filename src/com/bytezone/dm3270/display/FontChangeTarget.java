package com.bytezone.dm3270.display;

/*
 * O que o FontManager usa da tela - UM metodo.
 *
 * Ate este commit o FontManagerType1 recebia a Screen concreta. Era o ultimo dos onze
 * vazamentos de "this" do construtor da Screen que ainda nomeava a classe, e e o unico
 * REENTRANTE: o construtor do FontManagerType1 le as preferencias, monta a fonte e chama
 * fontChanged de volta - de dentro do construtor da Screen, que ainda nao terminou.
 *
 * O CONTRATO, que ate aqui nao estava escrito em lugar nenhum: quem implementa esta porta
 * tem de tolerar ser chamado ANTES de estar inteiro. A Screen tolera por duas guardas de nulo
 * em fontChanged - consolePane e screenPositions -, e e a segunda que faz a tela NAO ser
 * desenhada durante a construcao (ScreenConstructionTest).
 *
 * adjustStage e false quando a mudanca vem do usuario arrastando a janela (setFontToFit), e
 * true em todo o resto.
 *
 * Eram duas sobrecargas na Screen, a de um argumento so delegando com true. A porta tem uma,
 * e o FontManagerType1 passa o true ele mesmo.
 */
// -----------------------------------------------------------------------------------//
@FunctionalInterface
interface FontChangeTarget
// -----------------------------------------------------------------------------------//
{
  void fontChanged (FontDetails fontDetails, boolean adjustStage);
}
