package com.bytezone.dm3270.extended;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.extended.CommandHeader.DataType;
import com.bytezone.dm3270.extended.CommandHeader.RequestType;
import com.bytezone.dm3270.extended.CommandHeader.ResponseType;

// -----------------------------------------------------------------------------------//
@DisplayName ("CommandHeader - cabecalho de 5 bytes do TN3270E")
class CommandHeaderTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tipo de dado")
  class DataTypes
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "byte 0 = {0} -> {1}")
    @CsvSource ({ "0, TN3270_DATA", "1, SCS_DATA", "2, RESPONSE", "3, BIND_IMAGE",
                  "4, UNBIND", "5, NVT_DATA", "6, REQUEST", "7, SSCP_LU_DATA",
                  "8, PRINT_EOJ" })
    @DisplayName ("mapeia o primeiro byte para o tipo")
    void mapsDataType (int code, DataType expected)
    {
      byte[] header = { (byte) code, 0x00, 0x00, 0x00, 0x00 };

      assertEquals (expected, new CommandHeader (header).getDataType ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tipo de resposta")
  class ResponseTypes
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "TN3270_DATA + byte 2 = {0} -> {1}")
    @CsvSource ({ "0, NO_RESPONSE", "1, ERROR_RESPONSE", "2, ALWAYS_RESPONSE" })
    @DisplayName ("dados 3270 declaram o que o host espera de volta")
    void mapsRequestResponse (int code, ResponseType expected)
    {
      byte[] header = { 0x00, 0x00, (byte) code, 0x00, 0x00 };

      assertEquals (expected, new CommandHeader (header).getResponseType ());
    }

    @ParameterizedTest (name = "RESPONSE + byte 2 = {0} -> {1}")
    @CsvSource ({ "0, POSITIVE_RESPONSE", "1, NEGATIVE_RESPONSE" })
    @DisplayName ("uma resposta traz o resultado positivo ou negativo")
    void mapsResponseResult (int code, ResponseType expected)
    {
      byte[] header = { 0x02, 0x00, (byte) code, 0x00, 0x00 };

      assertEquals (expected, new CommandHeader (header).getResponseType ());
    }

    @Test
    @DisplayName ("REQUEST com 0x00 sinaliza condicao de erro limpa")
    void mapsRequestType ()
    {
      byte[] header = { 0x06, 0x00, 0x00, 0x00, 0x00 };

      CommandHeader commandHeader = new CommandHeader (header);

      assertEquals (RequestType.ERR_COND_CLEARED, commandHeader.getRequestType ());
      assertNull (commandHeader.getResponseType ());
    }

    @Test
    @DisplayName ("tipos sem resposta associada devolvem null")
    void typesWithoutResponse ()
    {
      byte[] header = { 0x03, 0x00, 0x00, 0x00, 0x00 };    // BIND_IMAGE

      CommandHeader commandHeader = new CommandHeader (header);

      assertNull (commandHeader.getResponseType ());
      assertNull (commandHeader.getRequestType ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("numero de sequencia")
  class Sequence
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le os bytes 3 e 4 como um short big endian")
    void readsSequence ()
    {
      byte[] header = { 0x00, 0x00, 0x00, (byte) 0x12, (byte) 0x34 };

      assertEquals (0x1234, new CommandHeader (header).getSequence ());
    }

    @Test
    @DisplayName ("o valor maximo nao vira negativo")
    void handlesMaxSequence ()
    {
      byte[] header = { 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF };

      assertEquals (65535, new CommandHeader (header).getSequence ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getTelnetData")
  class TelnetData
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("nao acrescenta IAC EOR (o cabecalho vai junto com os dados)")
    void doesNotAppendEor ()
    {
      byte[] header = { 0x00, 0x00, 0x00, 0x00, 0x01 };

      assertArrayEquals (header, new CommandHeader (header).getTelnetData ());
    }

    @Test
    @DisplayName ("sem 0xFF devolve o proprio array, sem copiar")
    void returnsSameArrayWhenNothingToEscape ()
    {
      CommandHeader commandHeader =
          new CommandHeader (new byte[] { 0x00, 0x00, 0x00, 0x00, 0x01 });

      assertSame (commandHeader.getData (), commandHeader.getTelnetData ());
    }

    @Test
    @DisplayName ("duplica os 0xFF do numero de sequencia")
    void escapesFF ()
    {
      byte[] header = { 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF };

      assertArrayEquals (
          new byte[] { 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                       (byte) 0xFF },
          new CommandHeader (header).getTelnetData ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class ToString
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("resume sequencia, tipo e resposta numa linha")
    void summarises ()
    {
      byte[] header = { 0x00, 0x00, 0x02, 0x00, 0x07 };

      assertEquals ("HDR: 0007, TN3270_DATA , , ALWAYS_RESPONSE",
          new CommandHeader (header).toString ());
    }
  }
}
