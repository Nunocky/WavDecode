#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include "common.h"

typedef struct WG {
  FILE *fw;
  uint32_t length;
} WG;


void WG_putUint8(WG *wg, uint8_t v)
{
  fwrite(&v, 1, 1, wg->fw);
  wg->length++;
}

void WG_putUint16(WG *wg, uint16_t v)
{
  WG_putUint8(wg, (v >> 0) & 0xff);
  WG_putUint8(wg, (v >> 8) & 0xff);
}

void WG_putUint32(WG *wg, uint32_t v)
{
  WG_putUint8(wg, (v >>  0) & 0xff);
  WG_putUint8(wg, (v >>  8) & 0xff);
  WG_putUint8(wg, (v >> 16) & 0xff);
  WG_putUint8(wg, (v >> 24) & 0xff);
}



void putData_1(WG *wg)
{
  uint16_t samples[] = {
    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,

    0,
    32767,
    0,
    -32767,
  };

  for (int i=0; i<32; i++) {
    WG_putUint16(wg, samples[i]);
  }
}

void putData_0(WG *wg)
{
  uint16_t samples[] = {
    0,
    23169,
    32767,
    23169,
    0,
    -23170,
    -32767,
    -23170,
    
    0,
    23169,
    32767,
    23169,
    0,
    -23170,
    -32767,
    -23170,
    
    0,
    23169,
    32767,
    23169,
    0,
    -23170,
    -32767,
    -23170,
    
    0,
    23169,
    32767,
    23169,
    0,
    -23170,
    -32767,
    -23170,
  };

  for (int i=0; i<32; i++) {
    WG_putUint16(wg, samples[i]);
  }

}





void putData_0_header(WG *wg)
{
  for (int i=0; i< 1 * SAMPLING_RATE/32; i++) {
      putData_0(wg);
  }
}

void putData_blank(WG *wg)
{
  for (int i=0; i< 1 * SAMPLING_RATE/ 2; i++) {
    WG_putUint16(wg, 0);
  }
}

void putData(WG *wg, uint8_t v) 
{
  for(int i=0; i<8; i++) {
    uint8_t bitmask = v & (1 << (7-i));
    if (bitmask) {
      putData_1(wg);
    }
    else {
      putData_0(wg);
    }
  }
}


void putData_String(WG *wg, char *str)
{
  uint8_t *p = (uint8_t *)str;

  while (*p != 0) {
    putData(wg, *p);
    p++;
  }
}



void output_Text(WG *wg, char *str)
{
  int len = strlen(str);
  char line[128];
  sprintf(line, "Content-Length:%d\n", len);

  putData_String(wg, line);
  putData_String(wg, "Content-Type:text/plain\n\n");
  putData_String(wg, str);
}



int main(int argc, char* argv[])
{
  FILE *fw = fopen("output_text.wav", "wb");
  if (!fw) {
    perror("fopen");
    return -1;
  }

  WG wg;

  wg.fw = fw;
  wg.length = 0;
  
  // --------------------
  WG_putUint8(&wg, 'R');
  WG_putUint8(&wg, 'I');
  WG_putUint8(&wg, 'F');
  WG_putUint8(&wg, 'F');

  WG_putUint32(&wg, 0);
  
  WG_putUint8(&wg, 'W');
  WG_putUint8(&wg, 'A');
  WG_putUint8(&wg, 'V');
  WG_putUint8(&wg, 'E');

  // --------------------
  WG_putUint8(&wg, 'f');
  WG_putUint8(&wg, 'm');
  WG_putUint8(&wg, 't');
  WG_putUint8(&wg, ' ');
  WG_putUint32(&wg, 16);
  WG_putUint16(&wg, 1);
  WG_putUint16(&wg, 1);
  WG_putUint32(&wg, SAMPLING_RATE);
  WG_putUint32(&wg, SAMPLING_RATE * 1 * 2);
  WG_putUint16(&wg, 2);
  WG_putUint16(&wg, 16);

  // --------------------
  WG_putUint8(&wg, 'd');
  WG_putUint8(&wg, 'a');
  WG_putUint8(&wg, 't');
  WG_putUint8(&wg, 'a');

  const uint32_t offset_data = wg.length;

  WG_putUint32(&wg, 0);

  // 0秒間 + 同期情報 1111_1111
  putData_0_header(&wg);

  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);
  putData_1(&wg);

  // コンテント情報
  output_Text(&wg, "Hello World!");





  
  const uint32_t offset_data_end = wg.length;

  // data length
  fseek(fw, offset_data, SEEK_SET);
  WG_putUint32(&wg, offset_data_end - offset_data - 4);
  wg.length -= 4;

  // total length
  fseek(fw, 4, SEEK_SET);
  WG_putUint32(&wg, offset_data_end - 8);
  wg.length -= 4;

  fclose(fw);
  return 0;
}
