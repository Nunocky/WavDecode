TARGETS = mk_text mk_jpeg mk_png

all: $(TARGETS)

mk_text: mk_text.c
	$(CC) -Wall -o $@ $^

mk_jpeg: mk_jpeg.c
	$(CC) -Wall -o $@ $^

mk_png: mk_png.c
	$(CC) -Wall -o $@ $^

clean:
	$(RM) $(TARGETS) *~  *.wav

.PHONY: all clean
