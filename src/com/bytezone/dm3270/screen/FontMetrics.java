package com.bytezone.dm3270.screen;

/*
 * As dimensoes de uma fonte, ja medidas, sem depender do toolkit grafico.
 *
 * Sao os quatro dados que o desenho de uma posicao de tela precisa: largura e altura da
 * celula, a linha de base, e o nome da fonte que realmente foi resolvida. Nada mais.
 *
 * Existe para desatar a ultima amarra entre o modelo de tela e o JavaFX. FontDetails, que
 * era o que ScreenContext carregava, mede um javafx.scene.text.Text para descobrir essas
 * dimensoes - portanto exige toolkit ativo, e as dimensoes variam com as fontes instaladas
 * na maquina. Guardando o resultado da medicao em vez do medidor, ScreenPosition passa a
 * ser instanciavel e testavel com valores conhecidos.
 *
 * O nome e o RESOLVIDO por Font.getName, que pode diferir do pedido quando a fonte nao
 * existe no sistema. ScreenContext.toString imprime esse nome no log, entao guardar o
 * pedido em vez do resolvido mudaria saida observavel.
 */
// -----------------------------------------------------------------------------------//
public record FontMetrics (String name, int width, int height, int ascent)
// -----------------------------------------------------------------------------------//
{
}
