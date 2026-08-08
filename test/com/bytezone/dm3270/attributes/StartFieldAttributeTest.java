package com.bytezone.dm3270.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("StartFieldAttribute - decodificacao do byte de atributo de campo")
class StartFieldAttributeTest
// -----------------------------------------------------------------------------------//
{
  private static final byte PROTECTED = 0x20;
  private static final byte NUMERIC = 0x10;
  private static final byte MODIFIED = 0x01;

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("bits individuais")
  class Bits
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("bit 2 (0x20) marca o campo como protegido")
    void protectedBit ()
    {
      assertTrue (new StartFieldAttribute (PROTECTED).isProtected ());
      assertFalse (new StartFieldAttribute ((byte) 0x00).isProtected ());
    }

    @Test
    @DisplayName ("bit 3 (0x10) marca o campo como numerico")
    void numericBit ()
    {
      assertFalse (new StartFieldAttribute (NUMERIC).isAlphanumeric ());
      assertTrue (new StartFieldAttribute ((byte) 0x00).isAlphanumeric ());
    }

    @Test
    @DisplayName ("bit 7 (0x01) marca o campo como modificado")
    void modifiedBit ()
    {
      assertTrue (new StartFieldAttribute (MODIFIED).isModified ());
      assertFalse (new StartFieldAttribute ((byte) 0x00).isModified ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("bits 4 e 5 - modo de exibicao")
  class DisplayBits
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("00 = intensidade normal, visivel, nao detectavel")
    void normalIntensity ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x00);

      assertFalse (attribute.isIntensified ());
      assertTrue (attribute.isVisible ());
      assertFalse (attribute.isHidden ());
      assertFalse (attribute.isDetectable ());
    }

    @Test
    @DisplayName ("01 = detectavel com caneta optica")
    void penDetectable ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x04);

      assertTrue (attribute.isDetectable ());
      assertFalse (attribute.isIntensified ());
      assertTrue (attribute.isVisible ());
    }

    @Test
    @DisplayName ("10 = alta intensidade e detectavel")
    void highIntensity ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x08);

      assertTrue (attribute.isIntensified ());
      assertTrue (attribute.isDetectable ());
      assertTrue (attribute.isVisible ());
    }

    @Test
    @DisplayName ("11 = oculto (campo de senha)")
    void hidden ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x0C);

      assertTrue (attribute.isHidden ());
      assertFalse (attribute.isVisible ());
      assertFalse (attribute.isIntensified ());
      assertFalse (attribute.isDetectable ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("regras derivadas")
  class DerivedRules
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("auto skip exige protegido E numerico")
    void automaticSkip ()
    {
      assertTrue (
          new StartFieldAttribute ((byte) (PROTECTED | NUMERIC)).isAutomaticSkip ());
      assertFalse (new StartFieldAttribute (PROTECTED).isAutomaticSkip ());
      assertFalse (new StartFieldAttribute (NUMERIC).isAutomaticSkip ());
      assertFalse (new StartFieldAttribute ((byte) 0x00).isAutomaticSkip ());
    }

    @Test
    @DisplayName ("setModified nao apaga o bit original vindo do host")
    void userModifiedDoesNotClearHostBit ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute (MODIFIED);

      attribute.setModified (false);

      assertTrue (attribute.isModified (), "o bit 7 original deve ser preservado");
    }

    @Test
    @DisplayName ("setModified(true) marca um campo nao modificado")
    void userCanSetModified ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x00);

      assertFalse (attribute.isModified ());
      attribute.setModified (true);
      assertTrue (attribute.isModified ());
    }

    @Test
    @DisplayName ("setExtended sinaliza atributo criado por SFE")
    void extendedFlag ()
    {
      StartFieldAttribute attribute = new StartFieldAttribute ((byte) 0x00);

      assertFalse (attribute.isExtended ());
      attribute.setExtended ();
      assertTrue (attribute.isExtended ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getAcronym")
  class Acronym
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ //
        "0x00, pAVidm",     // desprotegido, alfanumerico, visivel
        "0x20, PAVidm",     // protegido
        "0x30, PaVidm",     // protegido + numerico (auto skip)
        "0x0C, pAvidm",     // oculto
        "0x08, pAVIDm",     // alta intensidade
        "0x21, PAVidM" })   // protegido + modificado
    @DisplayName ("resume o atributo em 6 letras")
    void buildsAcronym (String hex, String expected)
    {
      byte value = (byte) Integer.decode (hex).intValue ();
      assertEquals (expected, new StartFieldAttribute (value).getAcronym ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("compile")
  class Compile
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("devolve sempre um codigo seguro da tabela de enderecos")
    void producesSafeCode ()
    {
      // o valor compilado nunca pode colidir com uma order do data stream, por isso
      // e mapeado atraves de BufferAddress.address
      byte compiled = StartFieldAttribute.compile (true, false, false, false, false);

      assertEquals (com.bytezone.dm3270.orders.BufferAddress.address[0x20], compiled);
    }

    @Test
    @DisplayName ("compilar e decodificar preserva a semantica dos bits")
    void roundTripsThroughAttribute ()
    {
      byte compiled = StartFieldAttribute.compile (true, true, false, false, true);
      StartFieldAttribute attribute = new StartFieldAttribute (compiled);

      // o codigo seguro mantem os 6 bits baixos originais
      assertTrue (attribute.isProtected ());
      assertFalse (attribute.isAlphanumeric ());
      assertTrue (attribute.isModified ());
      assertTrue (attribute.isAutomaticSkip ());
    }

    @Test
    @DisplayName ("todas as 32 combinacoes geram codigos distintos")
    void allCombinationsAreDistinct ()
    {
      boolean[] seen = new boolean[256];
      for (int i = 0; i < 32; i++)
      {
        byte compiled = StartFieldAttribute.compile ((i & 0x10) != 0, (i & 0x08) != 0,
            (i & 0x04) != 0, (i & 0x02) != 0, (i & 0x01) != 0);
        int value = compiled & 0xFF;

        assertFalse (seen[value], String.format ("codigo repetido: %02X", compiled));
        seen[value] = true;
      }
    }
  }
}
