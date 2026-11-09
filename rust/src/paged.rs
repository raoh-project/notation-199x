/// A value for every code point, held in three levels so that what repeats is held once at each: a
/// code point's block, of `1 << BLOCK` code points, has a run of page numbers in `middle`, and its
/// page, of `1 << PAGE` code points, holds the values in `leaf`.
///
/// `gen/Generate.java` writes the three levels and the sizes of a block and a page, which it chooses
/// for each table as those that hold it in the fewest bytes.
pub(crate) struct Paged<A: 'static, M: 'static, V: 'static, const BLOCK: u32, const PAGE: u32> {
    top: &'static [A],
    middle: &'static [M],
    leaf: &'static [V],
}

impl<A, M, V, const BLOCK: u32, const PAGE: u32> Paged<A, M, V, BLOCK, PAGE> {
    pub(crate) const fn new(
        top: &'static [A],
        middle: &'static [M],
        leaf: &'static [V],
    ) -> Paged<A, M, V, BLOCK, PAGE> {
        Paged { top, middle, leaf }
    }
}

impl<A, M, V, const BLOCK: u32, const PAGE: u32> Paged<A, M, V, BLOCK, PAGE>
where
    A: Copy + Into<usize>,
    M: Copy + Into<usize>,
    V: Copy,
{
    /// The value of `c`.
    #[inline]
    pub(crate) fn get(&self, c: char) -> V {
        let c = c as usize;
        let run: usize = self.top[c >> BLOCK].into();
        let page: usize =
            self.middle[run << (BLOCK - PAGE) | (c >> PAGE) & ((1 << (BLOCK - PAGE)) - 1)].into();
        self.leaf[page << PAGE | c & ((1 << PAGE) - 1)]
    }
}
