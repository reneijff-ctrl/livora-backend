import { ContentItem } from '../api/contentService';
import contentService from '../api/contentService';

export interface ContentState {
  publicContent: ContentItem[];
  isLoading: boolean;
  error: string | null;
}

type Listener = (state: ContentState) => void;

class ContentStore {
  private state: ContentState = {
    publicContent: [],
    isLoading: false,
    error: null,
  };
  private listeners: Set<Listener> = new Set();

  getState(): ContentState {
    return { ...this.state };
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private setState(update: Partial<ContentState>) {
    this.state = { ...this.state, ...update };
    this.notify();
  }

  private notify() {
    this.listeners.forEach((listener) => listener({ ...this.state }));
  }

  /**
   * Fetches public content only if it hasn't been fetched yet.
   */
  async fetchPublicContent(force = false): Promise<void> {
    // Guard: don't refetch if we already have content (unless forced)
    if (!force && this.state.publicContent.length > 0) {
      return;
    }

    if (this.state.isLoading) return;

    this.setState({ isLoading: true, error: null });
    try {
      const data = await contentService.getPublicContent();
      this.setState({ publicContent: data, isLoading: false });
    } catch (error: any) {
      console.error('Failed to fetch public content', error);
      this.setState({ isLoading: false, error: error.message || 'Failed to fetch content' });
    }
  }
}

export const contentStore = new ContentStore();
export default contentStore;
