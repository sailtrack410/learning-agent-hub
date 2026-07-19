import { describe, expect, it } from 'vitest';
import { courseArticleEditSnapshot } from './course-article';

describe('courseArticleEditSnapshot', () => {
  it('ignores attachment preview availability because it is generated system state', () => {
    const pending = JSON.stringify({
      type: 'doc',
      content: [{ type: 'attachment', attrs: { attachmentRefId: '1', previewAvailable: false, name: '课程资料.docx' } }],
    });
    const ready = JSON.stringify({
      type: 'doc',
      content: [{ type: 'attachment', attrs: { attachmentRefId: '1', previewAvailable: true, name: '课程资料.docx' } }],
    });

    expect(courseArticleEditSnapshot('标题', '摘要', pending, 'DRAFT'))
      .toBe(courseArticleEditSnapshot('标题', '摘要', ready, 'DRAFT'));
  });

  it('still detects teacher-authored content and publication changes', () => {
    const content = '{"type":"doc","content":[{"type":"paragraph"}]}';

    expect(courseArticleEditSnapshot('标题', '摘要', content, 'DRAFT'))
      .not.toBe(courseArticleEditSnapshot('新标题', '摘要', content, 'DRAFT'));
    expect(courseArticleEditSnapshot('标题', '摘要', content, 'DRAFT'))
      .not.toBe(courseArticleEditSnapshot('标题', '摘要', content, 'PUBLISHED'));
  });
});
