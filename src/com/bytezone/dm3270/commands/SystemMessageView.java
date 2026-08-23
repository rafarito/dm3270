package com.bytezone.dm3270.commands;

/*
 * O que o SystemMessage precisa mostrar na tela.
 *
 * SystemMessage e uma classe de protocolo: ela reconhece as mensagens que o host escreve na
 * tela (job submetido, job terminado, hora, data, PROFILE, console de IPL) a partir da
 * sequencia de orders que chegou. Duas dessas mensagens, porem, terminam em interface
 * grafica - e era ela mesma quem montava a interface, importando javafx.scene.control.Dialog,
 * javafx.scene.text.Font e javafx.application.Platform para dentro do pacote commands.
 *
 * Esta porta e a fronteira. Fica aqui, ao lado do consumidor, de proposito: assim a
 * dependencia aponta para dentro - quem sabe desenhar implementa o contrato de quem sabe
 * reconhecer, e nao o contrario. O protocolo diz o que aconteceu; a camada de interface
 * decide como isso aparece.
 */
// -----------------------------------------------------------------------------------//
public interface SystemMessageView
// -----------------------------------------------------------------------------------//
{
  /*
   * O host mandou a tela de PROFILE do TSO. Os dois textos vem crus, como o host os
   * escreveu: o primeiro traz os tokens CHAR(), LINE() e PREFIX(), o segundo o resto.
   */
  void showProfile (String profileMessageText1, String profileMessageText2);
}
