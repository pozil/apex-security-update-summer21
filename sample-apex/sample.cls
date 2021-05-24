public class EnforcePrivateGetter {
    @AuraEnabled
    public Integer counter { private get; set; }

    @AuraEnabled
    public static EnforcePrivateGetter GetRepro2()
    {
        EnforcePrivateGetter result = new EnforcePrivateGetter();
        result.counter = 2; 
        return result;
    }

    private class PagedResult {
        @AuraEnabled
        public Integer pageSize { get; private set; }

        public String notImportant { get; private set; }
    }
}