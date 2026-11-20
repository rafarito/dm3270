package com.bytezone.dm3270.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Usa getMethods() para checar toda a superficie herdada ao decompor ScreenTarget em papeis.
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenTarget - o contrato que o protocolo enxerga")
class ScreenTargetContractTest
// -----------------------------------------------------------------------------------//
{
  private static final List<String> CONTRACT = List.of (      //
      "addTSOCommand(String)",                                //
      "buildFields(WriteControlCharacter)",                   //
      "buildQueryReply()",                                    //
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
      "hostWriteCompleted(boolean,Consumer)",                 //
      "insertCursor(int)",                                    //
      "isKeyboardLocked()",                                   //
      "isTSOCommandScreen()",                                 //
      "lockKeyboard(String)",                                 //
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
  @DisplayName ("expoe exatamente os 33 metodos que o protocolo pode chamar")
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
