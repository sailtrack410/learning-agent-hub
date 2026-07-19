export function courseArticleEditSnapshot(
  title: string,
  summary: string,
  contentJson: string,
  status: 'DRAFT' | 'PUBLISHED',
) {
  return JSON.stringify({
    title,
    summary,
    content: normalizedArticleContent(contentJson),
    status,
  });
}

function normalizedArticleContent(contentJson: string) {
  try {
    const document = JSON.parse(contentJson) as Record<string, unknown>;
    removePreviewAvailability(document);
    return JSON.stringify(document);
  } catch {
    return contentJson;
  }
}

function removePreviewAvailability(node: Record<string, unknown>) {
  const attrs = node.attrs;
  if (attrs && typeof attrs === 'object' && !Array.isArray(attrs)) {
    delete (attrs as Record<string, unknown>).previewAvailable;
  }
  const content = node.content;
  if (Array.isArray(content)) {
    content.forEach((child) => {
      if (child && typeof child === 'object' && !Array.isArray(child)) {
        removePreviewAvailability(child as Record<string, unknown>);
      }
    });
  }
}
