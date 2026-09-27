package com.bytezone.dm3270.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * O contrato inteiro que a pilha de protocolo ve numa tela.
 *
 * Todo override de Buffer.process recebe um ScreenTarget, e Java nao deixa estreitar o tipo
 * do parametro num override: o que este tipo expoe, contando o que ele herda, e exatamente o
 * que as 25 implementacoes de process podem chamar. Reorganizar a interface em papeis nao
 * pode tirar nem acrescentar metodo algum a essa soma - senao um process deixa de compilar,
 * ou passa a enxergar o que nao via.
 *
 * Por isso a lista vai por extenso, com os tipos dos parametros, e getMethods () em vez de
 * getDeclaredMethods (): o que importa e a superficie, nao em que interface ela mora.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenTarget - o contrato que o protocolo enxerga")
class ScreenTargetContractTest
// -----------------------------------------------------------------------------------//
{
  private static final List<String> CONTRACT = List.of (      //
      "addTSOCommand(String)",                                //
      "buildFields(WriteControlCharacter)",                   //
      "buildQueryReply()",                                    //
      "checkRecording()",                                     //
      "clearScreen()",                                        //
      "draw()",                                               //
      "eraseAllUnprotected()",                                //
      "getFieldAt(int)",                                      //
      "getFieldCount()",                                      //
      "getPen()",                                             //
      "getScreenCursor()",                                    //
      "getScreenDimensions()",                                //
      "getScreenPosition(int)",                               //
      "getScreenPositions()",                                 //
      "getSystemMessage()",                                   //
      "getTSOCommandField()",                                 //
      "getTransferManager()",                                 //
      "insertCursor(int)",                                    //
      "isKeyboardLocked()",                                   //
      "isTSOCommandScreen()",                                 //
      "lockKeyboard(String)",                                 //
      "processPluginAuto()",                                  //
      "readBuffer()",                                         //
      "readModifiedFields(byte)",                             //
      "resetInsertMode()",                                    //
      "resetModified()",                                      //
      "resetPartition()",                                     //
      "restoreKeyboard()",                                    //
      "setCurrentScreen(ScreenOption)",                       //
      "setIsConsole()",                                       //
      "setReplyMode(byte,byte[])",                            //
      "soundAlarm()",                                         //
      "startPrinter()",                                       //
      "validate(int)");

  // ---------------------------------------------------------------------------------//
  @Test
  @DisplayName ("expoe exatamente os 34 metodos que o protocolo pode chamar")
  void exposesTheWholeContract ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (CONTRACT.stream ().collect (Collectors.joining ("\n")),
                  signatures (ScreenTarget.class));
  }

  // ---------------------------------------------------------------------------------//
  private static String signatures (Class<?> type)
  // ---------------------------------------------------------------------------------//
  {
    return Arrays.stream (type.getMethods ()).filter (method -> !method.isSynthetic ())
        .map (ScreenTargetContractTest::signature).sorted ()
        .collect (Collectors.joining ("\n"));
  }

  // ---------------------------------------------------------------------------------//
  private static String signature (Method method)
  // ---------------------------------------------------------------------------------//
  {
    return method.getName () + Arrays.stream (method.getParameterTypes ())
        .map (Class::getSimpleName).collect (Collectors.joining (",", "(", ")"));
  }
}
