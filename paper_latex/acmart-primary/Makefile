PAPER = seminar_paper

all: $(PAPER).pdf

$(PAPER).pdf: $(PAPER).tex references.bib acmart.cls ACM-Reference-Format.bst
	pdflatex $(PAPER)
	- bibtex $(PAPER)
	pdflatex $(PAPER)
	pdflatex $(PAPER)

clean:
	$(RM) *.log *.aux *.bbl *.blg *.out *.toc *.lof *.lot *.fls *.fdb_latexmk *.synctex.gz

distclean: clean
	$(RM) $(PAPER).pdf

.PHONY: all clean distclean
