package com.bytezone.dm3270.attributes;

/*
 * A cor de um atributo 3270, sem depender de nenhum toolkit grafico.
 *
 * Substitui javafx.scene.paint.Color na paleta de ColorAttribute e em tudo que a carrega
 * (ScreenContext, ContextManager, StartFieldAttribute), de modo que a camada de protocolo
 * possa ser compilada e testada sem interface grafica. A conversao para o tipo do JavaFX
 * acontece so na hora de desenhar, em FxPalette.
 *
 * DUAS PROPRIEDADES PRECISAM SER PRESERVADAS AO MEXER AQUI:
 *
 * 1. Identidade. ScreenContext.matches compara cores com ==, nao com equals, e o pool de
 *    ContextManager depende disso. As constantes abaixo sao as unicas instancias que a
 *    paleta usa, e tres posicoes do array de ColorAttribute apontam para WHITE_SMOKE
 *    (Neutral1, Neutral2 e White) - 16 slots, 14 objetos. Criar uma instancia nova para
 *    cada slot mudaria o tamanho do pool e as respostas de matches.
 *
 * 2. Componentes exatos. Os valores sao os das constantes JavaFX que estas substituem, e
 *    FxPalette reconstroi a cor com Color.rgb, que produz exatamente os mesmos doubles
 *    (componente / 255.0). PaletteFidelityTest verifica essa equivalencia cor por cor - e
 *    o que garante que o desenho continua identico ao pixel.
 */
// -----------------------------------------------------------------------------------//
public record TerminalColor (int red, int green, int blue, double opacity)
// -----------------------------------------------------------------------------------//
{
  public static final TerminalColor WHITE_SMOKE = rgb (245, 245, 245);
  public static final TerminalColor DODGER_BLUE = rgb (30, 144, 255);
  public static final TerminalColor RED = rgb (255, 0, 0);
  public static final TerminalColor PINK = rgb (255, 192, 203);
  public static final TerminalColor LIME = rgb (0, 255, 0);
  public static final TerminalColor TURQUOISE = rgb (64, 224, 208);
  public static final TerminalColor YELLOW = rgb (255, 255, 0);
  public static final TerminalColor BLACK = rgb (0, 0, 0);
  public static final TerminalColor DARK_BLUE = rgb (0, 0, 139);
  public static final TerminalColor ORANGE = rgb (255, 165, 0);
  public static final TerminalColor PURPLE = rgb (128, 0, 128);
  public static final TerminalColor PALE_GREEN = rgb (152, 251, 152);
  public static final TerminalColor PALE_TURQUOISE = rgb (175, 238, 238);
  public static final TerminalColor GREY = rgb (128, 128, 128);

  // ---------------------------------------------------------------------------------//
  public TerminalColor
  // ---------------------------------------------------------------------------------//
  {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255)
      throw new IllegalArgumentException (
          String.format ("componente fora de 0..255: (%d, %d, %d)", red, green, blue));

    if (opacity < 0 || opacity > 1)
      throw new IllegalArgumentException ("opacidade fora de 0..1: " + opacity);
  }

  // ---------------------------------------------------------------------------------//
  public static TerminalColor rgb (int red, int green, int blue)
  // ---------------------------------------------------------------------------------//
  {
    return new TerminalColor (red, green, blue, 1.0);
  }

  /*
   * Mesmo formato de javafx.scene.paint.Color.toString (0xrrggbbaa).
   *
   * Nao e cosmetico: ColorAttribute.getName cai neste toString quando a cor nao esta na
   * paleta, e ScreenContext.toString imprime o resultado no log. Mudar o formato mudaria a
   * saida observavel.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    return String.format ("0x%02x%02x%02x%02x", red, green, blue,
        Math.round (opacity * 255));
  }
}
