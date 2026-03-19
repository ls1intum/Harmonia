#import "/layout/cover.typ": *
#import "/layout/titlepage.typ": *
#import "/layout/disclaimer.typ": *
#import "/layout/transparency_ai_tools.typ": transparency_ai_tools as transparency_ai_tools_layout
#import "/layout/abstract.typ": *
#import "/utils/print_page_break.typ": *
#import "/layout/fonts.typ": *
#import "/utils/diagram.typ": in-outline

#let thesis(
  title: "",
  titleGerman: "",
  degree: "",
  program: "",
  examiner: "",
  supervisors: (),
  author: "",
  startDate: datetime,
  submissionDate: datetime,
  abstract_en: "",
  transparency_ai_tools: "",
  is_print: false,
  body,
) = {
  cover(
    title: title,
    degree: degree,
    program: program,
    author: author,
  )

  print_page_break(print: is_print, to: "even")

  disclaimer(
    title: title,
    degree: degree,
    author: author,
    submissionDate: submissionDate
  )
  transparency_ai_tools_layout(transparency_ai_tools)

  print_page_break(print: is_print)

  abstract(lang: "en")[#abstract_en]

  set page(
    margin: (left: 30mm, right: 30mm, top: 40mm, bottom: 40mm),
    numbering: none,
    number-align: center,
  )

  set text(
    font: fonts.body, 
    size: 12pt, 
    lang: "en"
  )
  
  show math.equation: set text(weight: 400)

  // --- Headings ---
  show heading: set block(below: 0.85em, above: 1.75em)
  show heading: set text(font: fonts.body)
  set heading(numbering: "1.1")
  // Reference first-level headings as "chapters"
  show ref: it => {
    let el = it.element
    if el != none and el.func() == heading and el.level == 1 {
      link(
        el.location(),
        [Chapter #numbering(
          el.numbering,
          ..counter(heading).at(el.location())
        )]
      )
    } else {
      it
    }
  }

  // --- Paragraphs ---
  set par(leading: 1em)


  // --- Figures ---
  show figure: set text(size: 0.85em)
  
  // --- Table of Contents ---
  show outline.entry.where(level: 1): it => {
    v(15pt, weak: true)
    strong(it)
  }
  outline(
    title: {
      text(font: fonts.body, 1.5em, weight: 700, "Contents")
      v(15mm)
    },
    indent: 2em
  )
  
  
  v(2.4fr)
  pagebreak()


    // Main body. Reset page numbering.
  set page(numbering: "1")
  counter(page).update(1)
  set par(justify: true, first-line-indent: 2em)

  // Start each chapter on a new page
  show heading.where(level: 1): it => {
    pagebreak(weak: true)
    it
  }
  body

  // List of figures.
  pagebreak()
  show outline: it => { // Show only the short caption here
    in-outline.update(true)
    it
    in-outline.update(false)
  }
  outline(
    title: [
      #text(font: fonts.body, 1.5em, weight: 700, "List of Figures")
      #v(6mm)
    ],
    target: figure.where(kind: image),
  )

  // List of tables.
  context[
    #if query(figure.where(kind: table)).len() > 0 {
      pagebreak()
      outline(
        title: [
          #text(font: fonts.body, 1.5em, weight: 700, "List of Tables")
          #v(6mm)
        ],
        target: figure.where(kind: table)
      )
    }
  ]

  // Appendix.
  pagebreak()
  heading(numbering: none)[Appendix A: Supplementary Material]
  include("/layout/appendix.typ")

  pagebreak()

  //Citation
  bibliography("/thesis.bib", style: "ieee")
}
